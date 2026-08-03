import org.scalatra._
import javax.servlet.ServletContext
import org.dbpedia.databus.ApiImpl
import org.dbpedia.databus.RdfConversions.JsonLDSerialiser
import org.dbpedia.databus.swagger.DatabusSwagger
import org.dbpedia.databus.swagger.api.DefaultApi

class ScalatraBootstrap extends LifeCycle {

  override def init(context: ServletContext) {
    val sw = new DatabusSwagger
    val cfg = ApiImpl.Config.fromServletContext(context)
    JsonLDSerialiser.configure(cfg)
    context.log(s"Appl config is: $cfg")
    context.mount(new DefaultApi()(sw, new ApiImpl(cfg)), "/*")
  }

}
