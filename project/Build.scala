import sbt._
import Keys._
import sbtrelease.ReleasePlugin._
import sbtprotobuf.ProtobufPlugin.protobufSettings

object Build extends Build {
  lazy val basicSettings = Seq(
    name                 := "eventstore-client",
    organization         := "com.geteventstore",
    scalaVersion         := "2.11.0",
    crossScalaVersions   := Seq("2.10.4", "2.11.0"),
    licenses             := Seq("BSD 3-Clause" -> url("http://raw.github.com/EventStore/EventStore.JVM/master/LICENSE")),
    homepage             := Some(new URL("http://github.com/EventStore/EventStore.JVM")),
    organizationHomepage := Some(new URL("http://geteventstore.com")),
    description          := "Event Store JVM Client",
    startYear            := Some(2013),
    scalacOptions        := Seq("-encoding", "UTF-8", "-unchecked", "-deprecation", "-feature"),
    libraryDependencies ++= Seq(Akka.actor, Akka.testkit, protobuf, specs2, mockito, codec, typesafeConfig))

  object Akka {
    val actor   = apply("actor")
    val testkit = apply("testkit") % "test"

    private def apply(x: String) = "com.typesafe.akka" %% "akka-%s".format(x) % "2.3.3"
  }

  val typesafeConfig = "com.typesafe" % "config" % "1.2.1"
  val codec          = "org.apache.directory.studio" % "org.apache.commons.codec" % "1.8"
  val protobuf       = "com.google.protobuf" % "protobuf-java" % "2.5.0"
  val specs2         = "org.specs2" %% "specs2" % "2.3.12" % "test"
  val mockito        = "org.mockito" % "mockito-all" % "1.9.5" % "test"

  def itFilter(name: String): Boolean = name endsWith "ITest"
  def specFilter(name: String): Boolean = name endsWith "Spec"

  lazy val IntegrationTest = config("it") extend Test

  lazy val root = Project(
    "eventstore-client",
    file("."),
    settings = basicSettings ++ Defaults.coreDefaultSettings ++ releaseSettings ++ protobufSettings ++ Scalariform.settings ++ Publish.settings)
    .configs(IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.testTasks): _*)
    .settings(
    testOptions       in Test            := Seq(Tests.Filter(specFilter)),
    testOptions       in IntegrationTest := Seq(Tests.Filter(itFilter)),
    parallelExecution in IntegrationTest := false)
}