package org.dbpedia.databus.vosloader

import better.files.File
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration.FiniteDuration

package object config extends Logging {

  lazy val loading = LoadingConfig

  lazy val persistence = PersistenceConfig

  lazy val virtuoso = VirtuosoConfig

  lazy val configRoot = {

    def externalConfig = sys.props.get("org.dbpedia.databus.vosloader.config").map(File(_)).map {

      case file if file.isRegularFile => file

      case noFile => sys.error(s"Provided no (regular) file at given configuration location: '${noFile.pathAsString}'")
    }

    externalConfig.fold({

      logger.info("Using default config from classpath")
      ConfigFactory.load("vosloader.conf")
    }) { configFile =>

      logger.info(s"Using config from file ${configFile}")
      ConfigFactory.parseFile(configFile.toJava)
    }
  }

  object LoadingConfig {

    protected def subConfig = configRoot.getConfig("loading")

    lazy val vosQueuesParentDir = subConfig.as[File]("vosQueuesParentDir")

    lazy val loadingInterval = subConfig.as[FiniteDuration]("loadingInterval")

    lazy val minUnmodifiedDurationInStorage = subConfig.as[FiniteDuration]("minUnmodifiedDurationInStorage")
  }

  object PersistenceConfig {

    protected def subConfig = configRoot.getConfig("persistence")

    lazy val fileSystemStorageLocation = subConfig.as[File]("fileSystemStorageLocation")
  }

  object VirtuosoConfig {

    protected def subConfig = configRoot.getConfig("virtuoso")

    lazy val host = subConfig.as[String]("host")
    lazy val user = subConfig.as[String]("user")
    lazy val password = subConfig.as[String]("password")

    lazy val maxConcurrentConnections = subConfig.as[Int]("maxConcurrentConnections")
    lazy val concurrentConnectionsToProcessorsRatio =
      subConfig.as[Double]("concurrentConnectionsToProcessorsRatio")
  }

  implicit val betterFileReader: ValueReader[File] = new ValueReader[File] {

    override def read(config: Config, path: String): File = {

      File(config.getString(path))
    }
  }
}
