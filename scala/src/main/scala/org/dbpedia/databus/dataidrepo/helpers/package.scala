package org.dbpedia.databus.dataidrepo

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

package object helpers {

  def urlEncode(str: String) = URLEncoder.encode(str, StandardCharsets.UTF_8.name())
}
