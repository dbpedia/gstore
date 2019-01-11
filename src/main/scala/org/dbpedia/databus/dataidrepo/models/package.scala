package org.dbpedia.databus.dataidrepo

import org.dbpedia.databus.dataidrepo.helpers.urlEncode

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

package object models {

  def dataIdIdentifierToIRI(identifier: String) = {

    s"http://databus.dbpedia.org/ns/repo-internal#dataid-${urlEncode(identifier)}"
  }
}
