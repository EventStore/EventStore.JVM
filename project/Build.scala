import sbt.Keys._
import sbt._
import sbtprotobuf.{ProtobufPlugin => PB}
import sbtrelease.ReleasePlugin._
import scoverage.ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages

object Build extends Build {
  lazy val basicSettings = Seq(
    name                 := "eventstore-client",
    organization         := "com.geteventstore",
    scalaVersion         := "2.11.8",
    licenses             := Seq("BSD 3-Clause" -> url("http://raw.github.com/EventStore/EventStore.JVM/master/LICENSE")),
    homepage             := Some(new URL("http://github.com/EventStore/EventStore.JVM")),
    organizationHomepage := Some(new URL("http://geteventstore.com")),
    description          := "Event Store JVM Client",
    startYear            := Some(2013),
    scalacOptions        := Seq("-encoding", "UTF-8", "-unchecked", "-deprecation", "-feature"),
    resolvers += "spray" at "http://repo.spray.io/",
    libraryDependencies ++= Seq(
      Akka.actor, Akka.testkit, Akka.stream, Akka.http, Akka.streamTestkit,
      typesafeConfig, codec, mockito, playJson,
      Joda.time, Joda.convert,
      Specs2.core, Specs2.mock,
      ReactiveStreams.streams, ReactiveStreams.tck)
  )

  object ReactiveStreams {
    val streams = apply("reactive-streams")
    val tck     = apply("reactive-streams-tck") % "test"

    private def apply(x: String) = "org.reactivestreams" % x % "1.0.0"
  }

  object Akka {
    val actor         = apply("akka-actor")
    val testkit       = apply("akka-testkit") % "test"
    val stream        = apply("akka-stream")
    val http          = apply("akka-http-experimental")
    val streamTestkit = apply("akka-stream-testkit") % "test"

    private def apply(x: String) = "com.typesafe.akka" %% x % "2.4.8"
  }

  object Specs2 {
    val core = apply("core")
    val mock = apply("mock")

    private def apply(x: String) = "org.specs2" %% s"specs2-$x" % "2.4.15" % "test"
  }

  val typesafeConfig = "com.typesafe" % "config" % "1.3.0"
  val playJson       = "com.typesafe.play" %% "play-json" % "2.5.4"
  val codec          = "org.apache.directory.studio" % "org.apache.commons.codec" % "1.8"
  val mockito        = "org.mockito" % "mockito-all" % "1.9.5" % "test"

  object Joda {
    val time    = "joda-time" % "joda-time" % "2.9.4"
    val convert = "org.joda" % "joda-convert" % "1.8.1"
  }

  lazy val IntegrationTest = config("it") extend Test
  lazy val ClusterTest = config("c") extend Test

  lazy val root = Project(
    "eventstore-client",
    file("."),
    settings = basicSettings ++ Defaults.coreDefaultSettings ++ releaseSettings ++ PB.protobufSettings ++ Scalariform.settings ++ Publish.settings)
    .configs(IntegrationTest, ClusterTest)
    .settings(
      version in PB.protobufConfig := "3.0.0",
      PB.runProtoc in PB.protobufConfig := { args =>
       com.github.os72.protocjar.Protoc.runProtoc("-v3.0.0" +: args.toArray)
      })
    .settings(inConfig(IntegrationTest)(Defaults.testTasks): _*)
    .settings(inConfig(ClusterTest)(Defaults.testTasks): _*)
    .settings(
      coverageExcludedPackages :=
        "eventstore.examples;eventstore.j;" +
        "eventstore.proto;eventstore.pipeline;" +
        "eventstore.tcp.EventStoreProtoFormats;" +
        "eventstore.tcp.MarkerByte;" +
        "eventstore.util.ToCoarsest",
      testOptions in Test := Seq(Tests.Filter(_ endsWith "Spec")),
      testOptions in IntegrationTest := Seq(Tests.Filter(_ endsWith "ITest")),
      testOptions in ClusterTest := Seq(Tests.Filter(_ endsWith "CTest")),
      parallelExecution in IntegrationTest := false,
      parallelExecution in ClusterTest := false)
}