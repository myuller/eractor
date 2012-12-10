name := "Eractor"

version := "0.1"

scalaVersion := "2.9.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test"

libraryDependencies +=  "com.typesafe.akka" % "akka-testkit" % "2.0.4" % "test"

libraryDependencies +=  "com.typesafe.akka" % "akka-actor" % "2.0.4"

autoCompilerPlugins := true

addCompilerPlugin("org.scala-lang.plugins" % "continuations" % "2.9.1")

scalacOptions += "-P:continuations:enable"
