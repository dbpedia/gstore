package org.dbpedia.databus.dataidrepo.models

import org.dbpedia.databus.dataidrepo.errors
import org.dbpedia.databus.dataidrepo.rdf.vocab._
import org.dbpedia.databus.shared.rdf.conversions._
import org.dbpedia.databus.shared.rdf.vocab._
import org.dbpedia.databus.shared.rdf.vocab

import org.apache.jena.datatypes.xsd.XSDDateTime
import org.apache.jena.graph.NodeFactory
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.{Model, Resource}
import org.apache.jena.vocabulary.RDF

import scala.collection.JavaConverters._
import scala.util.Try

import java.time.Instant
import java.util.{Calendar, Date}
import java.util.concurrent.atomic.AtomicInteger


object DataIdMetadata {

  def byIdentifier(id: String)(implicit ds: Dataset): Option[DataIdMetadata] = {

    implicit lazy val model = ds.getDefaultModel

    // assumes that dcterms:identifier values have primary key characteristic
    val dataIdRes = ds.getDefaultModel.listSubjectsWithProperty(dcterms.identifier, id).asScala
      .find(_.hasType(dataid.DataId))

    dataIdRes map { new DataIdMetadata(_) }
  }

  def getOrCreate(id: String, deploymentLocation: String, version: String)
    (implicit ds: Dataset): Try[DataIdMetadata] = Try {

    implicit lazy val model = ds.getDefaultModel

    byIdentifier(id) getOrElse {

      val dataIdRes = model.createResource(dataIdIdentifierToIRI(id))

      addMetadataStatements(dataIdRes)(id, deploymentLocation, version)

      new DataIdMetadata(dataIdRes)
    }
  }

  protected def addMetadataStatements(res: Resource)(id: String, version: String,deploymentLocation: String) = {

    implicit lazy val model = res.getModel

    res.addProperty(RDF.`type`, dataid.DataId)
      .addLiteral(dcterms.identifier, id)
      .addLiteral(schemaOrg.version, version)
      .addProperty(dataIdRepo.deploymentLocation, deploymentLocation.asAbsoluteIRI)
      .addLiteral(dcterms.modified, Calendar.getInstance())
  }
}

class DataIdMetadata(res: Resource) {

  protected implicit def errorGen = (errors.unexpectedDataIdFormat _)

  protected  implicit lazy val model = res.getModel

  def deploymentLocation: Resource = {

    res.getRequiredFunctionalProperty(dataIdRepo.deploymentLocation).coerceUriResource.get
  }

  def identifier: String = {

    res.getRequiredFunctionalProperty(dcterms.identifier).coerceLiteral.map(_.getLexicalForm).get
  }

  def modificationTime: Calendar = {

    res.getRequiredFunctionalProperty(dcterms.modified).coerceLiteral.collect({
      case dt: XSDDateTime => dt.asCalendar
    }).get
  }

  def update(version: String, deploymentLocation: String) = {

    val id = identifier
    res.removeProperties()
    DataIdMetadata.addMetadataStatements(res)(id, version, deploymentLocation)
  }
}


object DataIdDocument {

  def byIdentifier(id: String)(implicit ds: Dataset): Option[DataIdDocument] = {

    ds.getNamedModel(dataIdIdentifierToIRI(id)) match {

      case empty if empty.isEmpty => None

      case nonEmpty => Some(new DataIdDocument(nonEmpty))
    }
  }
}


class DataIdDocument(rdf: Model) {

  protected implicit def model = rdf

  def getSingleFiles = {

    val typeStatements = rdf.listStatements(null, RDF.`type`, dataid.SingleFile).asScala

    typeStatements map { stmt => new SingleFile(stmt.getSubject) }
  }
}

class SingleFile(res: Resource) {

  protected implicit def model = res.getModel

  implicit def errorGen = errors.unexpectedDataIdFormat _

  def associatedAgent = res.getRequiredFunctionalProperty(dataid.associatedAgent).coerceUriResource.get

  def downloadURL = res.getRequiredFunctionalProperty(dcat.downloadURL).coerceUriResource.get

  def signatureString = res.getRequiredFunctionalProperty(dataid.signature).coerceLiteral.map({
    _.getString
  }).get
}
