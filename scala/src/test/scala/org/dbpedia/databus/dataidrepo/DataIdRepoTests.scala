package org.dbpedia.databus.dataidrepo

import com.typesafe.scalalogging.LazyLogging
import org.scalatra.test.scalatest._

import java.io.StringReader

class DataIdRepoTests extends ScalatraFunSuite with LazyLogging {

  addServlet(classOf[DataIdRepo], "/*")

  test("GET / on DataIdRepo should return status 200") {
    get("/") {
      status should equal (200)
    }
  }
}
