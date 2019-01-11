package org.dbpedia.databus.dataidrepo.config

import org.dbpedia.databus.dataidrepo.errors

import better.files._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.EnumerationReader._
import net.ceedubs.ficus.readers.ValueReader


/**
  * Created by Markus Ackermann.
  */
class DataIdRepoConfig(val externalConfigPath: Option[String]) extends LazyLogging {

  lazy val persistence = PersistenceConfig

  lazy val configRoot = {

    logger.info(s"external config value: ${sys.props.get(DataIdRepoConfigKey)}")

    def externalConfig = externalConfigPath.map(File(_)).map {

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

    lazy val tdbLocation = subConfig.as[File]("tdbLocation")

    lazy val fileSystemStorageLocation = subConfig.as[File]("fileSystemStorageLocation")
  }

  implicit val betterFileReader: ValueReader[File] = new ValueReader[File] {

    override def read(config: Config, path: String): File = {

      File(config.getString(path))
    }
  }
}
