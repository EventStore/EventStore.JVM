import sbtprotobuf.ProtobufPlugin


name := "eventstore-client"

organization := "com.geteventstore"

scalaVersion := crossScalaVersions.value.last

crossScalaVersions := Seq("2.12.8")

releaseCrossBuild := true

licenses := Seq("BSD 3-Clause" -> url("http://raw.github.com/EventStore/EventStore.JVM/master/LICENSE"))

homepage := Some(new URL("http://github.com/EventStore/EventStore.JVM"))

organizationHomepage := Some(new URL("http://geteventstore.com"))

description := "Event Store JVM Client"

startYear := Some(2013)

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
//  "-Xfatal-warnings",
  "-Xlint:-missing-interpolator",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture"
)

scalacOptions in(Compile, doc) ++= Seq("-groups", "-implicits", "-no-link-warnings")

enablePlugins(ProtobufPlugin)

val AkkaVersion = "2.5.21"
val AkkaHttpVersion = "10.1.7"
val ReactiveStreamsVersion = "1.0.2"
val Specs2Version = "3.8.6" // Because of concurrency issues with specs2 3.8.7+

libraryDependencies ++= Seq(
  "org.reactivestreams" % "reactive-streams" % ReactiveStreamsVersion,
  "org.reactivestreams" % "reactive-streams-tck" % ReactiveStreamsVersion % Test,
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe" % "config" % "1.3.3",
  "io.spray" %%  "spray-json" % "1.3.5",
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "joda-time" % "joda-time" % "2.10.1",
  "org.joda" % "joda-convert" % "2.2.0",
  "org.scodec" %% "scodec-bits" % "1.1.9",
  "org.mockito" % "mockito-all" % "1.10.19" % Test,
  "org.specs2" %% "specs2-core" % Specs2Version % Test,
  "org.specs2" %% "specs2-mock" % Specs2Version % Test)

lazy val IntegrationTest = config("it") extend Test
lazy val ClusterTest = config("c") extend Test

version in ProtobufConfig := "3.0.0"

protobufRunProtoc in ProtobufConfig := { args =>
  com.github.os72.protocjar.Protoc.runProtoc("-v3.0.0" +: args.toArray)
}

lazy val root = Project("eventstore-client", file("."))
  .configs(IntegrationTest, ClusterTest)
  .settings(inConfig(IntegrationTest)(Defaults.testTasks): _*)
  .settings(inConfig(ClusterTest)(Defaults.testTasks): _*)
  .settings(
    testOptions in Test := Seq(Tests.Filter(_ endsWith "Spec")),
    testOptions in IntegrationTest := Seq(Tests.Filter(_ endsWith "ITest")),
    testOptions in ClusterTest := Seq(Tests.Filter(_ endsWith "CTest")),
    parallelExecution in IntegrationTest := false,
    parallelExecution in ClusterTest := false)

coverageExcludedPackages :=
  "eventstore.examples;eventstore.j;" +
    "eventstore.proto;" +
    "eventstore.tcp.EventStoreProtoFormats;" +
    "eventstore.tcp.MarkerBytes;" +
    "eventstore.util.ToCoarsest"

pomExtra in Global := {
  <scm>
    <url>git@github.com:EventStore/EventStore.JVM.git</url>
    <connection>scm:git:git@github.com:EventStore/EventStore.JVM.git</connection>
    <developerConnection>scm:git:git@github.com:EventStore/EventStore.JVM.git</developerConnection>
  </scm>
    <developers>
      <developer>
        <id>t3hnar</id>
        <name>Yaroslav Klymko</name>
        <email>t3hnar@gmail.com</email>
      </developer>
    </developers>
}

releasePublishArtifactsAction := PgpKeys.publishSigned.value

publishTo := sonatypePublishTo.value