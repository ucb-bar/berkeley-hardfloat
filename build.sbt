organization := "edu.berkeley.cs"

version := "1.2"

name := "hardfloat"

scalaVersion := "2.11.12"

// Chisel2 does not build with 2.12. The 2.12 cross-build is for chisel3 projects
crossScalaVersions := Seq("2.11.12", "2.12.4")

// Provide a managed dependency on chisel if -DchiselVersion="" issupplied on the command line.
libraryDependencies ++= (Seq("chisel").map {
  dep: String => sys.props.get(dep + "Version") map { "edu.berkeley.cs" %% dep % _ }}).flatten
