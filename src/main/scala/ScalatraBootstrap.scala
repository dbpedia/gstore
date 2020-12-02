import org.dbpedia.databus.dataidrepo.config.{DataIdRepoConfig, DataIdRepoConfigKey}
import org.dbpedia.databus.dataidrepo._
import com.typesafe.scalalogging.LazyLogging
import org.scalatra._
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import org.dbpedia.databus.swagger.DatabusSwagger
import org.dbpedia.databus.swagger.api.DatabusApi
import org.dbpedia.databus.swagger.model.{ApiResponse, BinaryBody, DataIdSignatureMeta, DataidFields, DataidFileUpload}
import org.scalactic.Snapshots._

import scala.util.Try


class ScalatraBootstrap extends LifeCycle with LazyLogging {

  implicit val sw = new DatabusSwagger

  override def init(context: ServletContext) {

    //todo: configure logging according to possible external logback configuration

    val databusConfInitParam = context.initParameters.get(DataIdRepoConfigKey)

    val requireDBpediaAccount  = context.initParameters
      .get("requireDBpediaAccount")
      .map(_.toBoolean)
      .getOrElse(false)

    context.log(
      "ScalatraBootstrap.init: " +
        snap(databusConfInitParam) +
        "require DBpedia Account " +
        requireDBpediaAccount
    )

    implicit val config = new DataIdRepoConfig(databusConfInitParam, requireDBpediaAccount)

    context.mount(new DataIdRepo, "/old/*")
    context.mount(new Appi(new Api), "/new/*")
  }
}

class Api extends DatabusApi {
  override def dataIdCreate(body: DataidFields)(request: HttpServletRequest): Try[BinaryBody] = ???

  override def dataidSubgraph(body: BinaryBody)(request: HttpServletRequest): Try[BinaryBody] = ???

  override def dataidSubgraphHash(body: BinaryBody)(request: HttpServletRequest): Try[DataIdSignatureMeta] = ???

  override def dataidUpload(body: DataidFileUpload, xClientCert: String)(request: HttpServletRequest): Try[ApiResponse] = Try {
    ApiResponse(Some(200), Some("dada!"), Some("data!"))
  }
}
