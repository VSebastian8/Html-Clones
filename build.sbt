val scala3Version = "3.6.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "Html-Clones",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    javaOptions += "-Djava.util.logging.config.file=src/main/resources/logging.properties",
    scalacOptions += "-Ywarn-value-discard",
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    libraryDependencies += "org.scala-lang" %% "toolkit" % "0.7.0",
    libraryDependencies += "net.ruippeixotog" %% "scala-scraper" % "3.1.2",
    libraryDependencies += "org.seleniumhq.selenium" % "selenium-java" % "3.14.0",
    libraryDependencies += "commons-io" % "commons-io" % "2.6"
  )
