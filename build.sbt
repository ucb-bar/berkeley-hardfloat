organization := "edu.berkeley.cs"

version := "1.5-SNAPSHOT"

name := "hardfloat"

scalaVersion := "2.12.15"

scalacOptions += "-Xsource:2.11"

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % "3.5.0-RC2" cross CrossVersion.full)
libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.5.0-RC2"
libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "3.2.0" % "test")
Test / testForkedParallel := true

publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { x => false }
// Don't add 'scm' elements if we have a git.remoteRepo definition,
//  but since we don't (with the removal of ghpages), add them in below.
pomExtra := <url>http://chisel.eecs.berkeley.edu/</url>
  <licenses>
    <license>
      <name>BSD-style</name>
      <url>http://www.opensource.org/licenses/bsd-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/ucb-bar/berkeley-hardfloat.git</url>
    <connection>scm:git:github.com/ucb-bar/berkeley-hardfloat.git</connection>
  </scm>
  <developers>
    <developer>
      <id>jhauser-ucberkeley</id>
      <name>John Hauser</name>
    </developer>
    <developer>
      <id>aswaterman</id>
      <name>Andrew Waterman</name>
    </developer>
    <developer>
      <id>yunsup</id>
      <name>Yunsup Lee</name>
    </developer>
  </developers>

publishTo := {
  val v = version.value
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  }
  else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}
