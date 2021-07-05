import java.nio.file.Paths

import org.scalatra._
import javax.servlet.ServletContext
import org.dbpedia.databus.ApiImpl
import org.dbpedia.databus.swagger.DatabusSwagger
import org.dbpedia.databus.swagger.api.DefaultApi
import sttp.model.Uri

class ScalatraBootstrap extends LifeCycle {

  override def init(context: ServletContext) {
    implicit val c = context
    //todo: configure logging according to possible external logback configuration

    val sw = new DatabusSwagger

    val schema = getParam("gitSchema").orElse(Some("http"))
    val host = getParam("gitHost").orElse(Some("localhost"))
    val port = getParam("gitPort").map(_.toInt)
    val user = getParam("gitApiUser")
    val pass = getParam("gitApiPass")
    val localGitRoot = getParam("localGitRoot").map(Paths.get(_))
    val virtUri = getParam("virtuosoUri").get
    val virtUser = getParam("virtuosoUser").get
    val virtPass = getParam("virtuosoPass").get

    val cfg = ApiImpl.Config(
      user,
      pass,
      schema,
      host,
      port,
      localGitRoot,
      Uri.parse(virtUri).right.get,
      virtUser,
      virtPass
    )
    context.log(s"Appl config is: $cfg")
    context.mount(new DefaultApi()(sw, new ApiImpl(cfg)), "/*")
  }

  private def getParam(name: String)(implicit context: ServletContext): Option[String] =
    Option(context.getInitParameter(name))

}
