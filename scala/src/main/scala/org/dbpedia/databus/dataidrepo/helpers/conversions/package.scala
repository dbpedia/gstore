package org.dbpedia.databus.dataidrepo.helpers

/**
  * Created by Markus Ackermann.
  */
package object conversions {

  implicit class TapableW[T](val anyVal: T) extends AnyVal {

    def tap(work: T => Unit) = {
      work apply anyVal
      anyVal
    }
  }
}
