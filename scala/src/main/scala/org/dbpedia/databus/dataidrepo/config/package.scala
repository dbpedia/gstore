package org.dbpedia.databus.dataidrepo

import better.files._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.EnumerationReader._
import net.ceedubs.ficus.readers.ValueReader


/**
  * Created by Markus Ackermann.
  */
package object config extends LazyLogging {

  lazy val persistence = PersistenceConfig

  lazy val configRoot = {

    logger.info(s"external config value: ${sys.props.get("org.dbpedia.databus.dataidrepo.config")}")

    def externalConfig = sys.props.get("org.dbpedia.databus.dataidrepo.config").map(File(_)).map {

      case file if file.isRegularFile => file

      case noFile => throw errors.configurationError(
        s"Provided no (regular) file at given configuration location: '${noFile.pathAsString}'")
    }

    externalConfig.fold({

      logger.info("Using default config from classpath")
      ConfigFactory.load("dataid-repo.conf")
    }) { configFile =>

      logger.info(s"Using config from file ${configFile}")
      ConfigFactory.parseFile(configFile.toJava)
    }
  }

  object PersistenceConfig {

    protected def subConfig = configRoot.getConfig("persistence")

    lazy val strategy = subConfig.as[PersistenceStrategy.Value]("strategy")

    lazy val tdbLocation = subConfig.as[File]("tdbLocation")(betterFileReader)

    lazy val fileSystemStorageLocation = subConfig.as[File]("fileSystemStorageLocation")(betterFileReader)
  }

  implicit val betterFileReader = new ValueReader[File] {

    override def read(config: Config, path: String): File = {

      File(config.getString(path))
    }
  }

  object PersistenceStrategy extends Enumeration {

    val TDB = Value("TDB")

    val Filesystem = Value("Filesystem")
  }
}
