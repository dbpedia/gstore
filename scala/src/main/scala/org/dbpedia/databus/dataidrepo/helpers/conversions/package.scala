package org.dbpedia.databus.dataidrepo.helpers

import better.files.{File => BetterFile}
import resource._
import scalaz.{@@, Tag}

import java.nio.file.Paths

package object conversions {

  implicit class TaggedW[T, Tag](val lf: T @@ Tag) extends AnyVal {

    def value = Tag.unwrap(lf)
  }

  implicit class BetterFileW(val bf: BetterFile) extends AnyVal {

    def normalized = BetterFile(bf.path.normalize())

    def deleteOnError: ManagedResource[BetterFile] = deleteOnError()

    def deleteOnError(swallowExceptions: Boolean = false): ManagedResource[BetterFile] = {

      implicit def deleteOnErrorResource = new Resource[BetterFile] {

        override def close(r: BetterFile): Unit = ()

        override def closeAfterException(r: BetterFile, t: Throwable): Unit = r.delete(swallowExceptions)
      }

      managed(bf)
    }
  }
}
