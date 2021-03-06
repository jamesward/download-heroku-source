package controllers

import java.io.File
import java.nio.file.Files
import javax.inject.Inject

import play.api.libs.Crypto
import play.api.Configuration
import play.api.mvc._
import utils.{Heroku, UnauthorizedError, ZipHelper}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try


class Application @Inject() (heroku: Heroku, config: Configuration, crypto: Crypto) extends Controller {

  // prepends a url if the assets.url config is set
  private def webJarUrl(file: String): String = {
    val path = WebJarAssets.locate(file)
    val baseUrl = routes.WebJarAssets.at(path).url
    config.getString("assets.url").fold(baseUrl)(_ + baseUrl)
  }

  private val HEROKU_TOKEN = "HEROKU_TOKEN"

  def index() = Action.async { request =>
    request.session.get(HEROKU_TOKEN).fold {
      Future.successful(Redirect(heroku.loginUrl))
    } { token =>
      implicit val herokuKey = crypto.decryptAES(token)
      heroku.apps.map { apps =>
        val appNames = apps.value.map(_.\("name").as[String]).sorted
        Ok(views.html.index(webJarUrl, appNames))
      } recover {
        case e: UnauthorizedError => Redirect(heroku.loginUrl).withNewSession
        case e: Exception => InternalServerError(e.getMessage)
      }
    }
  }

  def herokuOAuthCallback(code: String) = Action.async {
    heroku.login(code).map(_.\("access_token").asOpt[String]).flatMap { maybeToken =>
      maybeToken.fold(Future.failed[Result](new Exception("Access Token not found"))) { token =>
        val encryptedToken = crypto.encryptAES(token)
        Future.successful(Redirect(routes.Application.index()).withSession(HEROKU_TOKEN -> encryptedToken))
      }
    } recover { case e: Error =>
      Unauthorized("Could not login to Heroku")
    }
  }

  def download(app: String) = Action.async { request =>
    request.session.get(HEROKU_TOKEN).fold {
      Future.successful(Redirect(heroku.loginUrl))
    } { token =>
      implicit val herokuKey = crypto.decryptAES(token)

      heroku.builds(app).map { builds =>

        builds.value.lastOption.fold {
          BadRequest("No builds found")
        } { build =>
          val maybeSourceBlobUrl = (build \ "source_blob" \ "url").asOpt[String]

          maybeSourceBlobUrl.fold {
            InternalServerError("Could not get blob url")
          } { sourceBlobUrl =>

            val maybeDir = Try {
              val tmpDir = Files.createTempDirectory(app)
              ZipHelper.downloadAndExtractTarGz(sourceBlobUrl, tmpDir)
              tmpDir.toFile
            } recoverWith {
              // failed to get the source blob so get the git repo instead
              case e: Exception => heroku.gitRepo(app)
            }

            maybeDir.toOption.fold {
              BadRequest("Could not get the source")
            } { dir =>
              val tmpZip = Files.createTempFile(s"$herokuKey-$app", ".zip")

              val sources: Traversable[(File, String)] = tree(dir).map { file =>
                file -> (app + File.separator + file.getPath.stripPrefix(dir.getPath))
              }

              ZipHelper.zipNative(sources, tmpZip.toFile)

              dir.delete()

              Ok.sendFile(
                content = tmpZip.toFile,
                fileName = _ => s"$app.zip",
                onClose = tmpZip.toFile.delete
              )
            }

          }
        }
      } recover {
        case e: UnauthorizedError => Redirect(heroku.loginUrl).withNewSession
        case e: Exception => NotFound(s"Could not download source for $app\n${e.getMessage}")
      }
    }
  }

  // lifted from: http://stackoverflow.com/a/8340937/77409
  private def tree(root: File, skipHidden: Boolean = false): Stream[File] =
    if (!root.exists || (skipHidden && root.isHidden)) Stream.empty
    else root #:: (
      root.listFiles match {
        case null => Stream.empty
        case files => files.toStream.flatMap(tree(_, skipHidden))
      })

}
