import mill._
import mill.scalalib._
import mill.scalalib.publish._
import $file.common

object v {
  val scala = "2.13.10"
  val chiselCrossVersions = Map(
    "3.5.6" -> (ivy"edu.berkeley.cs::chisel3:3.5.6", ivy"edu.berkeley.cs:::chisel3-plugin:3.5.6"),
    "3.6.0" -> (ivy"edu.berkeley.cs::chisel3:3.6.0", ivy"edu.berkeley.cs:::chisel3-plugin:3.6.0"),
    "5.0.0" -> (ivy"org.chipsalliance::chisel:5.0.0", ivy"org.chipsalliance:::chisel-plugin:5.0.0"),
  )
  val scalatest = ivy"org.scalatest::scalatest:3.2.0"
  val scalapar = ivy"org.scala-lang.modules::scala-parallel-collections:1.0.4"
}

object hardfloat extends Cross[Hardfloat](v.chiselCrossVersions.keys.toSeq)

trait Hardfloat
  extends common.HardfloatModule
    with HardfloatPublishModule
    with Cross.Module[String] {

  override def scalaVersion = T(v.scala)

  override def millSourcePath = os.pwd / "hardfloat"

  def chiselModule = None

  def chiselPluginJar = None

  def chiselIvy = Some(v.chiselCrossVersions(crossValue)._1)

  def chiselPluginIvy = Some(v.chiselCrossVersions(crossValue)._2)
}

object hardfloatdut extends Cross[HardfloatDut](v.chiselCrossVersions.keys.toSeq)

trait HardfloatDut
  extends common.HardfloatTestModule
    with Cross.Module[String] {

  override def scalaVersion = T(v.scala)

  override def millSourcePath = os.pwd / "hardfloat" / "tests"

  def hardfloatModule = hardfloat(crossValue)

  def chiselModule = None

  def chiselPluginJar = None

  def chiselIvy = Some(v.chiselCrossVersions(crossValue)._1)

  def chiselPluginIvy = Some(v.chiselCrossVersions(crossValue)._2)

  def scalatestIvy = v.scalatest

  def scalaparIvy = v.scalapar
}


trait HardfloatPublishModule extends PublishModule {
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "edu.berkeley.cs",
    url = "http://chisel.eecs.berkeley.edu",
    licenses = Seq(License.`BSD-3-Clause`),
    versionControl = VersionControl.github("ucb-bar", "berkeley-hardfloat"),
    developers = Seq(
      Developer("jhauser-ucberkeley", "John Hauser", "https://www.colorado.edu/faculty/hauser/about/"),
      Developer("aswaterman", "Andrew Waterman", "https://aspire.eecs.berkeley.edu/author/waterman/"),
      Developer("yunsup", "Yunsup Lee", "https://aspire.eecs.berkeley.edu/author/yunsup/")
    )
  )

  def publishVersion = "1.5-SNAPSHOT"
}
