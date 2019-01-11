package org.dbpedia.databus.dataidrepo

package object errors {

  trait DataIdRepoError extends Exception

  class ConfigurationError(msg: String) extends Exception(msg) with DataIdRepoError

  class UnexpectedRdfFormatError(msg: String) extends Exception(msg) with DataIdRepoError

  class UnexpectedDataIdFormatError(msg: String) extends UnexpectedRdfFormatError(msg)

  class UnexpectedRequestFormatError(msg: String) extends DataIdRepoError

  def configurationError(msg: String) = new ConfigurationError(msg)

  def unexpectedRdfFormat(msg: String) = new UnexpectedRdfFormatError(msg)

  def unexpectedDataIdFormat(msg: String) =  new UnexpectedDataIdFormatError(msg)

  def unexpectedRequestFormatError(msg: String) = new UnexpectedRequestFormatError(msg)
}
