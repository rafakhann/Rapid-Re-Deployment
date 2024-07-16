ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    name := "Rapid-Re-Deployement",
    libraryDependencies ++= Seq(
      "io.kubernetes" % "client-java" % "15.0.1",
      "com.typesafe.play" %% "play-json" % "2.10.0-RC6",
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
      "org.slf4j" % "slf4j-simple" % "1.7.32"
    ),
  )