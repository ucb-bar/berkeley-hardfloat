package hardfloat.test

import logger.LazyLogging
import org.scalatest.ParallelTestExecution
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait HardfloatTester extends AnyFlatSpec with Matchers with ParallelTestExecution with LazyLogging {
  def exp(f: Int) = f match {
    case 16 => 5
    case 32 => 8
    case 64 => 11
  }

  def sig(f: Int) = f match {
    case 16 => 11
    case 32 => 24
    case 64 => 53
  }

  val roundings = Seq(
    "-rnear_even" -> "0",
    "-rminMag" -> "1",
    "-rmin" -> "2",
    "-rmax" -> "3",
    "-rnear_maxMag" -> "4",
    "-rodd" -> "6"
  )
}