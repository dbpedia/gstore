package org.dbpedia.databus

import org.scalatra.{Ok, ScalatraServlet}

class ExternalApiEmul extends ScalatraServlet {

  post("/oauth/*"){
    Ok(
      """
        |{"access_token": "token"}
        |""".stripMargin)
  }

  get("/api/v4/*"){
    Ok(
      """
        |{"id": "commit id?"}
        |""".stripMargin)
  }

  get("/api/v4/projects"){
    Ok(
      """
        |[{
        |"name":"kuckuck",
        |"id": 100
        |}]
        |""".stripMargin)
  }

  post("/api/v4/*"){
    Ok(
      """
        |{"id": "random id"}
        |""".stripMargin)
  }



  get("/virtu/*"){
    Ok("Virtuoso emul")
  }

  post("/virtu/*"){
    Ok("Virtuoso emul")
  }

}
