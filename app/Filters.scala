import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import javax.inject.Inject
import play.api.http.HttpFilters
import play.filters.gzip.GzipFilter
import play.api.http.HeaderNames
import play.api.mvc._

class Filters @Inject() (gzip: GzipFilter, onlyHttps: OnlyHttpsFilter) extends HttpFilters {

  val filters = Seq(gzip, onlyHttps)

}

class OnlyHttpsFilter extends Filter {
  def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    nextFilter(requestHeader).map { result =>
      requestHeader.headers.get(HeaderNames.X_FORWARDED_PROTO).filter(_ != "https").fold(result) { proto =>
        Results.MovedPermanently("https://" + requestHeader.host + requestHeader.uri)
      }
    }
  }
}