package org.dbpedia.sbt

import java.io.File

import collection.JavaConverters._
import io.swagger.codegen.CodegenConstants

object Codegen {

  private val basePackage = "org.dbpedia.databus.swagger"

  def generate(baseDir: File, outBase: File): Seq[File] = {
    System.setProperty(CodegenConstants.APIS, "")
    System.setProperty(CodegenConstants.MODELS, "")
    val gen = new DatabusGenerator(baseDir)
    gen.opts(configurator(outBase).toClientOptInput)
      .generate()
      .asScala
  }

  def configurator(outBase: File) = {
    val generatorClassName = classOf[DatabusScalatraCodegen].getCanonicalName
    val configurator = new DatabusConfigurator()
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