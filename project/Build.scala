import sbt._
import Keys._
import scalabuff.ScalaBuffPlugin._
import sbtrelease.ReleasePlugin._

object Build extends Build {
  lazy val basicSettings = Seq(
    name                 := "eventstore-client",
    organization         := "com.geteventstore",
    scalaVersion         := "2.10.3",
    licenses             := Seq("BSD 3-Clause" -> url("http://raw.github.com/EventStore/eventstorejvmclient/master/LICENSE")),
    homepage             := Some(new URL("http://github.com/EventStore/eventstorejvmclient")),
    organizationHomepage := Some(new URL("http://geteventstore.com")),
    description          := "Event Store JVM Client",
    startYear            := Some(2013),
    scalacOptions        := Seq("-encoding", "UTF-8", "-unchecked", "-deprecation", "-feature"),
    libraryDependencies ++= Seq(akka, akkaTestkit, scalabuff, junit, specs2, mockito, codec),
    scalabuffVersion := "1.3.6")

  object V {
    val akka = "2.2.3"
    val scalabuff = "1.3.6"
  }

  val akka        = "com.typesafe.akka"           %% "akka-actor"               % V.akka
  val akkaTestkit = "com.typesafe.akka"           %% "akka-testkit"             % V.akka      % "test"
  val codec       = "org.apache.directory.studio" %  "org.apache.commons.codec" % "1.8"
  val scalabuff   = "net.sandrogrzicic"           %% "scalabuff-runtime"        % V.scalabuff
  val junit       = "junit"                       %  "junit"                    % "4.11"      % "test"
  val specs2      = "org.specs2"                  %% "specs2"                   % "2.3.4"     % "test"
  val mockito     = "org.mockito"                 %  "mockito-all"              % "1.9.5"     % "test"

  def itFilter(name: String): Boolean = name endsWith "ITest"
  def specFilter(name: String): Boolean = name endsWith "Spec"

  lazy val IntegrationTest = config("it") extend Test

  lazy val root = Project(
    "eventstore-client",
    file("."),
    settings = basicSettings ++ Defaults.defaultSettings ++ releaseSettings ++ scalabuffSettings ++ Format.settings ++ Publish.settings)
    .configs(ScalaBuff, IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.testTasks): _*)
    .settings(
    testOptions       in Test            := Seq(Tests.Filter(specFilter)),
    testOptions       in IntegrationTest := Seq(Tests.Filter(itFilter)),
    parallelExecution in IntegrationTest := false,
    scalabuffVersion := V.scalabuff)
}