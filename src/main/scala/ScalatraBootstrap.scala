import org.dbpedia.databus.dataidrepo.config.{DataIdRepoConfig, DataIdRepoConfigKey}
import org.dbpedia.databus.dataidrepo._
import com.typesafe.scalalogging.LazyLogging
import org.scalatra._
import javax.servlet.ServletContext
import org.dbpedia.databus.{ApiImpl, RemoteGitlabHttpClient}
import org.dbpedia.databus.swagger.DatabusSwagger
import org.dbpedia.databus.swagger.api.DefaultApi
import org.scalactic.Snapshots._

class ScalatraBootstrap extends LifeCycle with LazyLogging {

  override def init(context: ServletContext) {
    implicit val c = context
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

    val sw = new DatabusSwagger

    val schema = getParam("gitSchema").getOrElse("http")
    val host = getParam("gitHost").getOrElse("localhost")
    val port = getParam("gitPort").map(_.toInt)
    val token = getParam("gitApiToken").get
    val authCheckUrl = getParam("authCheckUrl").get
    context.log(s"Git host: $host")

    val cfg = ApiImpl.Config(
      token,
      schema,
      host,
      port,
      authCheckUrl
    )

    context.mount(new DefaultApi()(sw, new ApiImpl(cfg)), "/new/*")
  }

  private def getParam(name: String)(implicit context: ServletContext): Option[String] =
    Option(context.getInitParameter(name))

}
