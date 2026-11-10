package org.dbpedia.databus

import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/** Serves a JSON-LD context document over HTTP for integration tests. */
class JsonldContextHttpServer(contextBytes: Array[Byte], path: String = "/res/context.jsonld") {

  private val fetchCount = new AtomicInteger(0)
  private val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)

  server.createContext(path, (exchange: HttpExchange) => {
    fetchCount.incrementAndGet()
    exchange.getResponseHeaders.add("Content-Type", "application/ld+json")
    exchange.sendResponseHeaders(200, contextBytes.length)
    exchange.getResponseBody.write(contextBytes)
    exchange.close()
  })

  server.start()

  def port: Int = server.getAddress.getPort

  def contextUrl: String = s"http://127.0.0.1:$port$path"

  def requests: Int = fetchCount.get()

  def stop(): Unit = server.stop(0)
}
