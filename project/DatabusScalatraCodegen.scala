package org.dbpedia.sbt

import java.io.File
import java.nio.file.Paths
import java.util

import io.swagger.codegen.auth.AuthParser
import io.swagger.codegen.{ClientOptInput, ClientOpts}
import io.swagger.codegen.config.CodegenConfigurator
import io.swagger.codegen.languages.ScalatraServerCodegen
import io.swagger.models.Swagger
import io.swagger.models.auth.AuthorizationValue
import io.swagger.parser.SwaggerParser
import org.apache.commons.lang3.StringUtils

class DatabusScalatraCodegen(baseDir: File) extends ScalatraServerCodegen {
  this.apiDocTemplateFiles.clear()
  this.apiTemplateFiles.clear()
  this.apiTemplateFiles.put("databus_api.mustache", ".scala")
  setTemplateDir(
    baseDir.toPath
      .resolve("project")
      .resolve("scalatra")
      .toAbsolutePath.toString
  )
  setCommonTemplateDir(templateDir())
}

class DatabusConfigurator(baseDir: File) extends CodegenConfigurator {

  override def toClientOptInput: ClientOptInput = {
    this.setSystemProperties()
    val config = new DatabusScalatraCodegen(baseDir)
    config.setInputSpec(getInputSpec)
    config.setOutputDir(getOutputDir)
    config.setSkipOverwrite(isSkipOverwrite)
    config.setSkipAliasGeneration(false)
    config.setIgnoreFilePathOverride(getIgnoreFileOverride)
    config.setRemoveOperationIdPrefix(getRemoveOperationIdPrefix)
    config.instantiationTypes.putAll(getInstantiationTypes)
    config.typeMapping.putAll(getTypeMappings)
    config.importMapping.putAll(getImportMappings)
    config.languageSpecificPrimitives.addAll(getLanguageSpecificPrimitives)
    config.reservedWordsMappings.putAll(getReservedWordsMappings)
    this.checkAndSetAdditionalProperty(getApiPackage, "apiPackage")
    this.checkAndSetAdditionalProperty(getModelPackage, "modelPackage")
    this.checkAndSetAdditionalProperty(getInvokerPackage, "invokerPackage")
    this.checkAndSetAdditionalProperty(getGroupId, "groupId")
    this.checkAndSetAdditionalProperty(getArtifactId, "artifactId")
    this.checkAndSetAdditionalProperty(getArtifactVersion, "artifactVersion")
    this.checkAndSetAdditionalProperty(getTemplateDir, toAbsolutePathStr(getTemplateDir), "templateDir")
    this.checkAndSetAdditionalProperty(getModelNamePrefix, "modelNamePrefix")
    this.checkAndSetAdditionalProperty(getModelNameSuffix, "modelNameSuffix")
    this.checkAndSetAdditionalProperty(getGitUserId, "gitUserId")
    this.checkAndSetAdditionalProperty(getGitRepoId, "gitRepoId")
    this.checkAndSetAdditionalProperty(getReleaseNote, "releaseNote")
    this.checkAndSetAdditionalProperty(getHttpUserAgent, "httpUserAgent")
    if (StringUtils.isNotEmpty(getLibrary)) config.setLibrary(getLibrary)
    config.additionalProperties.putAll(getAdditionalProperties)
    val input: ClientOptInput = (new ClientOptInput).config(config)
    val authorizationValues: util.List[AuthorizationValue] = AuthParser.parse(getAuth)
    val swagger: Swagger = (new SwaggerParser).read(getInputSpec, authorizationValues, true)
    input.opts(new ClientOpts).swagger(swagger)
    input
  }

  def setSystemProperties(): Unit = {
    val var1 = getSystemProperties.entrySet.iterator
    while (var1.hasNext) {
      val entry = var1.next
      System.setProperty(entry.getKey, entry.getValue)
    }
  }

  def toAbsolutePathStr(str: String) = {
    if (StringUtils.isNotEmpty(str)) Paths.get(str).toAbsolutePath.toString
    else str
  }

  def checkAndSetAdditionalProperty(property: String, valueToSet: String, propertyKey: String): Unit = {
    if (StringUtils.isNotEmpty(property)) addAdditionalProperty(propertyKey, valueToSet)
  }

  def checkAndSetAdditionalProperty(property: String, propertyKey: String): Unit = {
    this.checkAndSetAdditionalProperty(property, property, propertyKey)
  }

}

