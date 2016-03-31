name := "download-heroku-source"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

resolvers += Classpaths.typesafeReleases

libraryDependencies ++= Seq(
  ws,
  filters,
  "org.apache.commons" % "commons-compress" % "1.10",
  "org.eclipse.jgit"   % "org.eclipse.jgit" % "4.2.0.201601211800-r",
  "org.scala-sbt"     %% "io"               % "0.13.11",
  "org.webjars"       %% "webjars-play"     % "2.4.0-2",
  "org.webjars"        % "bootstrap"        % "3.3.4"
)

routesGenerator := InjectedRoutesGenerator