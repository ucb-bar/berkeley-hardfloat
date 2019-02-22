organization := "edu.berkeley.cs"

version := "1.2-SNAPSHOT"

name := "hardfloat"

scalaVersion := "2.12.8"

scalacOptions += "-Xsource:2.11"

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

val defaultVersions = Map("chisel3" -> "3.2-SNAPSHOT")

// Provide a managed dependency on chisel if -DchiselVersion="" issupplied on the command line.
libraryDependencies ++= (Seq("chisel3").map {
  dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
})
