package org.dbpedia.databus.dataidrepo.models

import org.dbpedia.databus.dataidrepo.errors
import org.dbpedia.databus.shared.rdf.conversions._
import org.dbpedia.databus.shared.rdf.vocab

import org.apache.jena.rdf.model.{Model, Resource}
import org.apache.jena.vocabulary.RDF

import scala.collection.JavaConverters._

class DataIdDocument(rdf: Model) {

  lazy val dataid = vocab.dataid.inModel(rdf)

  def getSingleFiles = {

    val typeStatements = rdf.listStatements(null, RDF.`type`, dataid.SingleFile).asScala

    typeStatements map { stmt => new SingleFile(stmt.getSubject) }
  }
}

class SingleFile(res: Resource) {

  lazy val dataid = vocab.dataid.inModel(res.getModel)

  lazy val dcat = vocab.dcat.inModel(res.getModel)

  implicit def errorGen = errors.unexpectedDataIdFormat _

  def associatedAgent = res.getRequiredFunctionalProperty(dataid.associatedAgent).coerceUriResource.get

  def downloadURL = res.getRequiredFunctionalProperty(dcat.downloadURL).coerceUriResource.get

  def signatureString = res.getRequiredFunctionalProperty(dataid.signature).coerceLiteral.map({
    _.getString
  }).get
}
