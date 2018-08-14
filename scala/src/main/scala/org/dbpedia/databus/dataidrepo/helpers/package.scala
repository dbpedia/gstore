package org.dbpedia.databus.dataidrepo

import resource._

/**
  * Created by Markus Ackermann.
  */
package object helpers {

  def resourceAsStream(name: String, classLoader: ClassLoader = Thread.currentThread().getContextClassLoader()) = {

    managed(classLoader.getResourceAsStream(name))
  }
}
