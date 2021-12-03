package org.dbpedia.sbt

import java.io.File

import collection.JavaConverters._
import io.swagger.codegen.{CodegenConstants, DefaultGenerator}

object Codegen {

  private val basePackage = "org.dbpedia.databus.swagger"

  def generate(baseDir: File, outBase: File): Seq[File] = {
    System.setProperty(CodegenConstants.APIS, "")
    System.setProperty(CodegenConstants.MODELS, "")
//    enabling this is useful for mustache generator variables
//    System.setProperty("debugModels", "")
//    System.setProperty("debugOperations", "")
    val gen = new DefaultGenerator()
    gen.opts(configurator(baseDir, outBase).toClientOptInput)
      .generate()
      .asScala
  }

  def configurator(baseDir: File, outBase: File) = {
    val generatorClassName = classOf[DatabusScalatraCodegen].getCanonicalName
    val configurator = new DatabusConfigurator(baseDir)
    val inputSpec = "swagger.yaml"
    configurator.setLang(generatorClassName)
    configurator.setInputSpec(inputSpec)
    configurator.setOutputDir(outBase.toPath.toAbsolutePath.toString)
    configurator.setIgnoreFileOverride("true")
    configurator.setApiPackage(basePackage + ".api")
    configurator.setModelPackage(basePackage + ".model")
    configurator
  }


}