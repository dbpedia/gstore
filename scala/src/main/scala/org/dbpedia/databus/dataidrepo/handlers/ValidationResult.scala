package org.dbpedia.databus.dataidrepo.handlers

import org.dbpedia.databus.dataidrepo.handlers.{SeverityOfViolation => Severity}
import org.dbpedia.databus.dataidrepo.handlers.SeverityOfViolation.{SeverityOfViolation => Severity}

object ValidationResult {

  def bySeverity(severity: Severity)(shortMessage: String): ValidationResult = {

    severity match {

      case Severity.Error => Error(shortMessage)

      case Severity.Warning => Warning(shortMessage)
    }
  }

  def bySeverity(severity: Severity)(shortMessage: String, details: String): ValidationResult = {

    severity match {

      case Severity.Error => Error(shortMessage, details)

      case Severity.Warning => Warning(shortMessage, details)
    }
  }
}

sealed trait ValidationResult {

  def isBlocker: Boolean

  def shortMessage: String

  def details: Option[String]
}

trait WithResult[T] {

  def result: T
}

object Pass {

  def apply(shortMessage: String, details: String): Pass = new Pass(shortMessage, Some(details))

  def apply[T](shortMessage: String, result: T): PassWithResult[T] = new PassWithResult[T](shortMessage, result)

  def apply[T](shortMessage: String, result: T, details: String) = {

    new PassWithResult[T](shortMessage, result, Some(details))
  }
}

case class Pass(shortMessage: String, details: Option[String] = None)
  extends ValidationResult {

  override def isBlocker: Boolean = false
}

case class PassWithResult[T](shortMessage: String, result: T, details: Option[String] = None)
  extends ValidationResult with WithResult[T] {

  override def isBlocker: Boolean = false
}

object Error {

  def apply(shortMessage: String, details: String): Error = new Error(shortMessage, Some(details))
}

case class Error(shortMessage: String, details: Option[String] = None)
  extends ValidationResult {

  override def isBlocker: Boolean = true
}

object Warning {

  def apply(shortMessage: String, details: String): Warning = new Warning(shortMessage, Some(details))
}

case class Warning(shortMessage: String, details: Option[String] = None)
  extends ValidationResult {

  override def isBlocker: Boolean = false
}

object SeverityOfViolation extends Enumeration {

  type SeverityOfViolation = Value

  val Warning, Error = Value
}
