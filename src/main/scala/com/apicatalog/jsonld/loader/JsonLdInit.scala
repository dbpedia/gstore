package com.apicatalog.jsonld.loader

import com.apicatalog.jsonld.http.media.MediaType

object JsonLdInit {
  def initLoader: DocumentLoader = {
    val dl = HttpLoader.defaultInstance()
    dl.fallbackContentType(MediaType.JSON_LD)
    dl
  }

}
