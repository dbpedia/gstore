import org.dbpedia.databus.dataidrepo.config.{DataIdRepoConfig, DataIdRepoConfigKey}
import org.dbpedia.databus.dataidrepo._
import com.typesafe.scalalogging.LazyLogging
import org.scalatra._
import javax.servlet.ServletContext
import org.dbpedia.databus.ApiImpl
import org.dbpedia.databus.swagger.DatabusSwagger
import org.dbpedia.databus.swagger.api.DefaultApi
import org.scalactic.Snapshots._

class ScalatraBootstrap extends LifeCycle with LazyLogging {

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

    val sw = new DatabusSwagger
    context.mount(new DefaultApi()(sw, new ApiImpl), "/new/*")
  }
}
