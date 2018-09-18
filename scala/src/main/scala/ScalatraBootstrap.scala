import org.dbpedia.databus.dataidrepo._
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {

    //todo: configure logging according to possible external logback configuration

    context.mount(new DataIdRepo, "/*")
  }
}
