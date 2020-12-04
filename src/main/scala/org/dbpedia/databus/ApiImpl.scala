package org.dbpedia.databus

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import javax.servlet.http.HttpServletRequest
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.dbpedia.databus.swagger.api.DatabusApi
import org.dbpedia.databus.swagger.model.{ApiResponse, BinaryBody, DataIdSignatureMeta, DataidFileUpload}
import scalaj.http.Base64

import scala.util.Try



class ApiImpl extends DatabusApi {

  override def dataidSubgraph(body: BinaryBody)(request: HttpServletRequest): Try[BinaryBody] = ???

  override def dataidSubgraphHash(body: BinaryBody)(request: HttpServletRequest): Try[DataIdSignatureMeta] = ???

  override def dataidUpload(body: DataidFileUpload, xClientCert: String)(request: HttpServletRequest): Try[ApiResponse] = Try {
    val data = RdfConversions.processFile(body.file.dataBase64)
    ApiResponse(Some(200), Some(new String(data)), Some("data!"))
  }

  override def createGroup(groupId: Object, body: BinaryBody)(request: HttpServletRequest): Try[ApiResponse] = ???

  override def createVersion(versionId: Object, body: BinaryBody)(request: HttpServletRequest): Try[ApiResponse] = ???

  override def deleteGroup(groupId: Object)(request: HttpServletRequest): Try[ApiResponse] = ???

  override def deleteVersion(versionId: Object)(request: HttpServletRequest): Try[ApiResponse] = ???

  override def getGroup(groupId: Object)(request: HttpServletRequest): Try[Unit] = ???

  override def getVersion(versionId: Object)(request: HttpServletRequest): Try[Unit] = ???
}


object RdfConversions {

  def processFile(fileBase64: String): Array[Byte] = {
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(Base64.decode(fileBase64))
    Try(RDFDataMgr.read(model, dataStream, Lang.JSONLD))
      .getOrElse(RDFDataMgr.read(model, dataStream, Lang.TURTLE))

    val str = new ByteArrayOutputStream()
    RDFDataMgr.write(str, model, Lang.TURTLE)
    str.toByteArray
  }


}


