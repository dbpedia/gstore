import com.typesafe.scalalogging.LazyLogging
import org.scalatra._
import javax.servlet.ServletContext
import org.dbpedia.databus.ApiImpl
import org.dbpedia.databus.swagger.DatabusSwagger
import org.dbpedia.databus.swagger.api.DefaultApi

class ScalatraBootstrap extends LifeCycle with LazyLogging {

  override def init(context: ServletContext) {
    implicit val c = context
    //todo: configure logging according to possible external logback configuration

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

    context.mount(new DefaultApi()(sw, new ApiImpl(cfg)), "/*")
  }

  private def getParam(name: String)(implicit context: ServletContext): Option[String] =
    Option(context.getInitParameter(name))

}
