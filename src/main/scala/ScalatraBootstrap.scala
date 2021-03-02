import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.LazyLogging
import org.scalatra._
import javax.servlet.ServletContext
import org.dbpedia.databus.{ApiImpl, Crypto}
import org.dbpedia.databus.swagger.DatabusSwagger
import org.dbpedia.databus.swagger.api.DefaultApi
import sttp.model.Uri

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
    val shaclUri = getParam("shaclUri").get
    val virtUri = getParam("virtuosoUri").get
    val virtUser = getParam("virtuosoUser").get
    val virtPass = getParam("virtuosoPass").get
    val databusKeyFile = getParam("databusKeystoreFile").get
    val databusKeystorePass = getParam("databusKeystorePass").get
    val databusKeyPass = getParam("databusKeyPass").get
    val databusKeyAlias = getParam("databusKeyAlias").get

    context.log(s"Git host: $host")

    val key = Crypto.getKeyPairFromKeystore(databusKeyFile, databusKeyAlias, databusKeystorePass, databusKeyPass)

    val cfg = ApiImpl.Config(
      token,
      schema,
      host,
      port,
      authCheckUrl,
      shaclUri,
      Uri.parse(virtUri).right.get,
      virtUser,
      virtPass,
      key._2
    )

    context.mount(new DefaultApi()(sw, new ApiImpl(cfg)), "/*")
  }

  private def getParam(name: String)(implicit context: ServletContext): Option[String] =
    Option(context.getInitParameter(name))

}
