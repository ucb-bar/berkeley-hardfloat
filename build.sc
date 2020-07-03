import mill._
import mill.scalalib._
import mill.scalalib.publish._
import coursier.maven.MavenRepository

// The following stanza is searched for and used when preparing releases.
// Please retain it.
// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
val defaultVersions = Map(
  "chisel3" -> "3.3-SNAPSHOT",
)

def getVersion(dep: String, org: String = "edu.berkeley.cs") = {
  val version = sys.env.getOrElse(dep + "Version", defaultVersions(dep))
  ivy"$org::$dep:$version"
}
object hardfloat extends hardfloat 

class hardfloat extends ScalaModule with SbtModule with PublishModule { m =>
  def scalaVersion = "2.12.8"
  // different scala version shares same sources
  // mill use foo/2.11.12 foo/2.12.11 as millSourcePath by default
  override def millSourcePath = super.millSourcePath / os.up

  def chisel3Module: Option[PublishModule] = None

  def chisel3IvyDeps = if(chisel3Module.isEmpty) Agg(
    getVersion("chisel3")
  ) else Agg.empty[Dep]

  def moduleDeps = super.moduleDeps ++ chisel3Module 

  def ivyDeps = super.ivyDeps() ++ chisel3IvyDeps

  def scalacOptions = Seq("-Xsource:2.11")

  def publishVersion = "1.3-SNAPSHOT"
  
  def artifactName = "hardfloat"

  def repositories() = super.repositories ++ Seq(
    MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"),
    MavenRepository("https://oss.sonatype.org/service/local/staging/deploy/maven2")
  )

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
