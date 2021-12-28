import mill._
import mill.scalalib._
import mill.scalalib.publish._
import coursier.maven.MavenRepository

object v {
  val scala = "2.12.15"
  val chisel3 = ivy"edu.berkeley.cs::chisel3:3.5-SNAPSHOT"
  val chisel3Plugin = ivy"edu.berkeley.cs:::chisel3-plugin:3.5-SNAPSHOT"
  val scalatest = ivy"org.scalatest::scalatest:3.2.0"
}

object hardfloat extends hardfloat 

class hardfloat extends ScalaModule with SbtModule with PublishModule { m =>
  def scalaVersion = v.scala
  // different scala version shares same sources
  // mill use foo/2.11.12 foo/2.12.11 as millSourcePath by default
  override def millSourcePath = super.millSourcePath / os.up

  def chisel3Module: Option[PublishModule] = None

  def chisel3IvyDeps = if(chisel3Module.isEmpty) Agg(
    v.chisel3
  ) else Agg.empty[Dep]

  def chisel3PluginJar: Option[PathRef] = None

  def chisel3PluginIvyDeps = if(chisel3Module.isEmpty) Agg(
    v.chisel3Plugin
  ) else Agg.empty[Dep]

  def scalacPluginIvyDeps = super.ivyDeps() ++ chisel3PluginIvyDeps

  def scalacPluginClasspath = super.scalacPluginClasspath() ++ chisel3PluginJar

  def moduleDeps = super.moduleDeps ++ chisel3Module 

  def ivyDeps = super.ivyDeps() ++ chisel3IvyDeps

  def scalacOptions = Seq("-Xsource:2.11")

  def publishVersion = "1.5-SNAPSHOT"
  
  def artifactName = "hardfloat"

  def repositories() = super.repositories ++ Seq(
    MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"),
    MavenRepository("https://oss.sonatype.org/service/local/staging/deploy/maven2")
  )

  object test extends Tests {
    def ivyDeps = Agg(v.scalatest)
    def testFramework = "org.scalatest.tools.Framework"
  }

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
}
