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

    val schema = getParam("gitSchema").getOrElse("http")
    val host = getParam("gitHost").getOrElse("localhost")
    val port = getParam("gitPort").map(_.toInt)
    val user = getParam("gitApiUser").get
    val pass = getParam("gitApiPass").get
    val virtUri = getParam("virtuosoUri").get
    val virtUser = getParam("virtuosoUser").get
    val virtPass = getParam("virtuosoPass").get

    val cfg = ApiImpl.Config(
      user,
      pass,
      schema,
      host,
      port,
      Uri.parse(virtUri).right.get,
      virtUser,
      virtPass
    )
    context.log(
      s"""Appl config is:
         |$host:$port
         |$user
         |$virtUri
         |$virtUser
         |""".stripMargin)
    context.mount(new DefaultApi()(sw, new ApiImpl(cfg)), "/*")
  }

  private def getParam(name: String)(implicit context: ServletContext): Option[String] =
    Option(context.getInitParameter(name))

}
