package org.dbpedia.databus.dataidrepo

/**
  * Created by Markus Ackermann.
  * No rights reserved.
  */
package object errors {

  trait DataIdRepoError extends Exception

  class UnexpectedDataIdFormatError(msg: String) extends Exception(msg) with DataIdRepoError

  def unexpectedDataIdFormat(msg: String) =  new UnexpectedDataIdFormatError(msg)
}
