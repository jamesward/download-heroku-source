package utils

import java.io.File
import java.nio.file.Files
import javax.inject.Inject

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import play.api.Configuration
import play.api.http.{HeaderNames, Status}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsArray, JsValue}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.Results.EmptyContent

import scala.concurrent.Future
import scala.util.Try

class Heroku @Inject() (ws: WSClient, config: Configuration) {

  val oauthId = config.getString("heroku.oauth.id").get
  val oauthSecret = config.getString("heroku.oauth.secret").get

  val loginUrl = "https://id.heroku.com/oauth/authorize?client_id=%s&response_type=code&scope=%s".format(oauthId, "global")

  private def ws(path: String)(implicit accessToken: String): WSRequest = {
    ws
      .url("https://api.heroku.com" + path)
      .withHeaders(
        HeaderNames.ACCEPT -> "application/vnd.heroku+json; version=3",
        HeaderNames.AUTHORIZATION -> s"Bearer $accessToken"
      )
  }

  def login(code: String): Future[JsValue] = {
    ws
    .url("https://id.heroku.com/oauth/token")
    .withQueryString(
      "grant_type" -> "authorization_code",
      "code" -> code,
      "client_secret" -> oauthSecret
    )
    .post(EmptyContent())
    .flatMap { response =>
      response.status match {
        case Status.OK => Future.successful(response.json)
        case _ => Future.failed(new Exception(response.body))
      }
    }
  }

  def apps()(implicit accessToken: String): Future[JsArray] = {
    ws(s"/apps").get().ok(_.json.as[JsArray])
  }

  def appInfo(app: String)(implicit accessToken: String): Future[JsValue] = {
    ws(s"/apps/$app").get().ok(_.json)
  }

  def builds(app: String)(implicit accessToken: String): Future[JsArray] = {
    ws(s"/apps/$app/builds").get().ok(_.json.as[JsArray])
  }

  def gitRepo(app: String)(implicit accessToken: String): Try[File] = {
    Try {
      val tmpDir = Files.createTempDirectory(s"$accessToken-$app")

      val cloneCommand = Git.cloneRepository()
      cloneCommand.setURI(s"https://git.heroku.com/$app.git")
      cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider("token", accessToken))
      cloneCommand.setDirectory(tmpDir.toFile)

      cloneCommand.call()

      tmpDir.toFile
    }
  }

}