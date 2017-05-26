organization := "edu.berkeley.cs"

version := "1.2"

name := "hardfloat"

scalaVersion := "2.11.6"

// Provide a managed dependency on chisel if -DchiselVersion="" issupplied on the command line.
libraryDependencies ++= sys.props.get("chiselVersion").map {
  "edu.berkeley.cs" %% "chisel3" % _
}.toSeq
