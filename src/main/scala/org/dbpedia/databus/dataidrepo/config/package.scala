package org.dbpedia.databus.dataidrepo

package object config {

  lazy val DataIdRepoConfigKey = "org.dbpedia.databus.dataidrepo.config"

  object PersistenceStrategy extends Enumeration {

    val TDB = Value("TDB")

    val Filesystem = Value("Filesystem")
  }
}
