import org.dbpedia.databus.dataidrepo.config.{DataIdRepoConfig, DataIdRepoConfigKey}
import org.dbpedia.databus.dataidrepo._

import com.typesafe.scalalogging.LazyLogging
import org.scalatra._
import javax.servlet.ServletContext
import org.scalactic.Snapshots._


class ScalatraBootstrap extends LifeCycle with LazyLogging {

  override def init(context: ServletContext) {

    //todo: configure logging according to possible external logback configuration

    val databusConfInitParam = context.initParameters.get(DataIdRepoConfigKey)

    context.log("ScalatraBootstrap.init: " + snap(databusConfInitParam))

    implicit val config = new DataIdRepoConfig(databusConfInitParam)

    context.mount(new DataIdRepo, "/*")
  }
}
