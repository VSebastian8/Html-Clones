val scala3Version = "3.6.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "Html-Clones",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    libraryDependencies += "org.scala-lang" %% "toolkit" % "0.7.0",
    libraryDependencies += "net.ruippeixotog" %% "scala-scraper" % "3.1.2"
  )
