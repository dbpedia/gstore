package org.dbpedia.databus.dataidrepo.helpers

import better.files.File
import com.typesafe.scalalogging.LazyLogging
import monix.execution.atomic.{Atomic, AtomicInt}
import org.scalactic.Requirements._
import org.scalactic.TypeCheckedTripleEquals._
import resource._

class ReentrantLockFile(path: File) extends LazyLogging {

 protected lazy val semaphore: AtomicInt = Atomic(0)

  def enter() = {
    this.synchronized {
      if (semaphore.get === 0) {
        requireState(path.notExists)
        logger.debug(s"creating lock file ${path.pathAsString}")
        path.touch()
        semaphore.increment()
      } else {
        requireState(path.isRegularFile)
        logger.debug(s"incrementing lock count on ${path.pathAsString}")
        semaphore.increment()
      }
    }

    import LockLease.lockLeaseResource

    managed(new LockLease(this))
  }

  protected[helpers] def exit() = this.synchronized {
    logger.debug(s"decreasing lock count on ${path.pathAsString}")
    if (semaphore.decrementAndGet() === 0) {
      requireState(path.isRegularFile)
      logger.debug(s"removing lock file ${path.pathAsString}")
      path.delete()
    }

    ()
  }
}

object LockLease {

  implicit def lockLeaseResource: Resource[LockLease] = new Resource[LockLease] {

    override def close(r: LockLease): Unit = r.exit()
  }
}

class LockLease(lock: ReentrantLockFile) {

  def exit() = lock.exit()
}

