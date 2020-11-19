package org.dbpedia.databus.swagger

import org.scalatra.swagger.{ApiInfo, Swagger}

object DatabusApiInfo extends ApiInfo(
  title = "Databus API",
  description = "Docs for the DatabusApi",
  termsOfServiceUrl = "",
  contact = "apiteam@scalatra.org",
  license = "",
  licenseUrl = "http://opensource.org/licenses/MIT")

class DatabusSwagger extends Swagger(Swagger.SpecVersion, "1.0.0", DatabusApiInfo)
