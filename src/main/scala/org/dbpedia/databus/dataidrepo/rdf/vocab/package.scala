package org.dbpedia.databus.dataidrepo.rdf

import org.dbpedia.databus.shared.rdf.vocab._

import org.apache.jena.rdf.model.Model

package object vocab {

  object global {

    def dataid = DataId
  }

  def dataIdRepo(implicit model: Model) = DataIdRepo.inModel(model)


  trait DataIdRepoVocab extends RDFNamespaceVocab {

    def namespace = "http://databus.dbpedia.org/ns/dataid-repo#"

    /**
      * range: rdfs:Resource [non-anonymous - an URL]
      */
    lazy val deploymentLocation = property("deploymentLocation")
  }

  object DataIdRepo extends RDFNamespace with DataIdRepoVocab {

    override def inModel(contextModel: Model) = {

      new RDFNamespaceInModel with DataIdRepoVocab {

        override def model: Model = contextModel
      }
    }
  }
}
