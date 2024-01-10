package org.dbpedia.databus

import org.scalatra.{BadRequest, Ok, ScalatraServlet}

class ExternalApiEmul extends ScalatraServlet {

  post("/oauth/*") {
    Ok(
      """
        |{"access_token": "token"}
        |""".stripMargin)
  }

  get("/api/v4/*") {
    Ok(
      """
        |{"id": "commit id?"}
        |""".stripMargin)
  }

  get("/api/v4/projects") {
    Ok(
      """
        |[{
        |"name":"kuckuck",
        |"id": 100
        |}]
        |""".stripMargin)
  }

  post("/api/v4/*") {
    Ok(
      """
        |{"id": "random id"}
        |""".stripMargin)
  }


  get("/virtu/*") {
    Ok("Virtuoso emul")
  }

  post("/virtu/*") {
    if (request.multiParameters.get("query").get.exists(_.contains("Raise VirtuosoException"))) {
      BadRequest("virtuoso.jdbc4.VirtuosoException: SQ074: Line 38: SP030: SPARQL compiler, line 5: syntax error")
    } else {
      Ok("Virtuoso emul")
    }
  }

}
