name := "download-heroku-source"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  ws,
  filters,
  "org.apache.commons" % "commons-compress" % "1.9",
  "org.eclipse.jgit"   % "org.eclipse.jgit" % "4.0.0.201505260635-rc2",
  "org.scala-sbt"     %% "io"               % "0.13.8",
  "org.webjars"       %% "webjars-play"     % "2.4.0-1",
  "org.webjars"        % "bootstrap"        % "3.3.4",
  "org.scalatestplus" %% "play"             % "1.2.0" % "test"
)

routesGenerator := InjectedRoutesGenerator