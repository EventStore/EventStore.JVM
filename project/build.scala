import sbt._
import Keys._
import scalabuff.ScalaBuffPlugin._

object Build extends Build {
  lazy val basicSettings = Seq(
    name                 := "eventstore-client",
    organization         := "com.geteventstore",
    version              := "0.1-SNAPSHOT",
    scalaVersion         := "2.10.2",
    organizationHomepage := Some(new URL("http://geteventstore.com")),
    description          := "Event Store JVM Client",
    startYear            := Some(2013),
    scalacOptions        := Seq("-encoding", "UTF-8", "-unchecked", "-deprecation", "-feature"),
    libraryDependencies ++= Seq(akka, akkaTestkit, scalabuff, junit, specs2, mockito),
    resolvers := Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"),
    scalabuffVersion := "1.3.6")

  object V {
    val akka = "2.2.1"
    val scalabuff = "1.3.6"
  }

  val akka        = "com.typesafe.akka" %% "akka-actor"        % V.akka
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit"      % V.akka      % "test"
  val scalabuff   = "net.sandrogrzicic" %% "scalabuff-runtime" % V.scalabuff
  val junit       = "junit"             %  "junit"             % "4.11"      % "test"
  val specs2      = "org.specs2"        %% "specs2"            % "2.2.2"     % "test"
  val mockito     = "org.mockito"       %  "mockito-all"       % "1.9.5"     % "test"

  def itFilter(name: String): Boolean = name endsWith "ITest"
  def specFilter(name: String): Boolean = name endsWith "Spec"

  lazy val IntegrationTest = config("it") extend Test

  lazy val root = Project(
    "main",
    file("."),
    settings = basicSettings ++ Defaults.defaultSettings ++ scalabuffSettings ++ Format.settings)
    .configs(ScalaBuff, IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.testTasks): _*)
    .settings(
    testOptions       in Test            := Seq(Tests.Filter(specFilter)),
    testOptions       in IntegrationTest := Seq(Tests.Filter(itFilter)),
    parallelExecution in IntegrationTest := false,
    scalabuffVersion := V.scalabuff)
}