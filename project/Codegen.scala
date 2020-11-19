package org.dbpedia.sbt

import java.io.File
import collection.JavaConverters._

import io.swagger.codegen.{CodegenConstants, DefaultGenerator}
import io.swagger.codegen.config.CodegenConfigurator

object Codegen {

  private val basePackage = "org.dbpedia.databus.swagger"

  def generate(outBase: File): Seq[File] = {
    System.setProperty(CodegenConstants.APIS, "")
    System.setProperty(CodegenConstants.MODELS, "")
    val gen = new DefaultGenerator()
    gen.opts(configurator(outBase).toClientOptInput)
      .generate()
      .asScala
  }

  def configurator(outBase: File) = {
    val configurator = new CodegenConfigurator()
    val inputSpec = "swagger.yaml"
    configurator.setLang("io.swagger.codegen.languages.ScalatraServerCodegen")
    configurator.setInputSpec(inputSpec)
    configurator.setOutputDir(outBase.toPath.toAbsolutePath.toString)
    configurator.setIgnoreFileOverride("true")
    configurator.setApiPackage(basePackage + ".api")
    configurator.setModelPackage(basePackage + ".model")
    configurator
  }


}
