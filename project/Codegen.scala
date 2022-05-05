package org.dbpedia.sbt

import java.io.File

import io.swagger.codegen.config.CodegenConfigurator
import io.swagger.codegen.languages.StaticDocCodegen

import collection.JavaConverters._
import io.swagger.codegen.{CodegenConstants, DefaultGenerator}

object Codegen {

  private val basePackage = "org.dbpedia.databus.swagger"
  private val inputSpec = "swagger.yaml"

  def docs(outBase: File): Seq[File] = {
    val config =  new CodegenConfigurator()
    config.setLang(classOf[StaticDocCodegen].getCanonicalName)
    config.setInputSpec(inputSpec)
    config.setOutputDir(outBase.toPath.toAbsolutePath.toString)
    val re = gen(config)
    re
  }

  def generate(baseDir: File, outBase: File): Seq[File] = {
    System.setProperty(CodegenConstants.APIS, "")
    System.setProperty(CodegenConstants.MODELS, "")
    //    enabling this is useful for mustache generator variables
    //    System.setProperty("debugModels", "")
    //    System.setProperty("debugOperations", "")
    gen(configurator(baseDir, outBase))
  }

  def configurator(baseDir: File, outBase: File) = {
    val generatorClassName = classOf[DatabusScalatraCodegen].getCanonicalName
    val configurator = new DatabusConfigurator(baseDir)
    configurator.setLang(generatorClassName)
    configurator.setInputSpec(inputSpec)
    configurator.setOutputDir(outBase.toPath.toAbsolutePath.toString)
    configurator.setIgnoreFileOverride("true")
    configurator.setApiPackage(basePackage + ".api")
    configurator.setModelPackage(basePackage + ".model")
    configurator
  }

  private def gen(configurator: CodegenConfigurator): Seq[File] = {
    val gen = new DefaultGenerator()
    gen.opts(configurator.toClientOptInput)
      .generate()
      .asScala
  }

}