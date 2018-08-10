package org.dbpedia.databus.dataidrepo

import resource._

/**
  * Created by Markus Ackermann.
  * No rights reserved.
  */
package object helpers {

  def resourceAsStream(name: String, classLoader: ClassLoader = Thread.currentThread().getContextClassLoader()) = {

    managed(classLoader.getResourceAsStream(name))
  }

  implicit class TapableW[T](val anyVal: T) extends AnyVal {

    def tap(work: T => Unit) = {
      work apply anyVal
      anyVal
    }
  }
}
