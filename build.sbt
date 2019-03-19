import sbtprotobuf.ProtobufPlugin
import com.github.os72.protocjar.Protoc
import Dependencies._

lazy val commonSettings = Seq(

  organization := "com.geteventstore",
  scalaVersion := crossScalaVersions.value.last,
  crossScalaVersions := Seq("2.12.8"),
  releaseCrossBuild := true,
  licenses := Seq("BSD 3-Clause" -> url("http://raw.github.com/EventStore/EventStore.JVM/master/LICENSE")),
  homepage := Some(new URL("http://github.com/EventStore/EventStore.JVM")),
  organizationHomepage := Some(new URL("http://geteventstore.com")),
  description := "Event Store JVM Client",
  startYear := Some(2013),

  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Xfatal-warnings",
    "-Xlint:-missing-interpolator",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Xfuture"
  ),

  scalacOptions in(Compile, doc) ++= Seq("-groups", "-implicits", "-no-link-warnings"),

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
        <developer>
          <id>ahjohannessen</id>
          <name>Alex Henning Johannessen</name>
          <email>ahjohannessen@gmail.com</email>
        </developer>        
      </developers>
  },

  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishTo := sonatypePublishTo.value
)

///

lazy val IntegrationTest = config("it") extend Test
lazy val ClusterTest     = config("c") extend Test

lazy val root = project.in(file("."))
  .settings(commonSettings)
  .settings(skip in publish := true)
  .dependsOn(core, client, examples)
  .aggregate(core, client, examples)

lazy val core = project
  .settings(commonSettings)
  .settings(
    moduleName := "eventstore-client-core",
    version in ProtobufConfig := protobufVersion,
    protobufRunProtoc in ProtobufConfig := { args => Protoc.runProtoc(s"-v$protobufVersion" +: args.toArray) },
    coverageExcludedPackages :=
      "eventstore.proto;" +
      "eventstore.tcp.EventStoreProtoFormats;" +
      "eventstore.tcp.MarkerBytes;"
  ).settings(
    libraryDependencies ++=
      Seq(`scodec-bits`, `ts-config`) ++ testDeps(specs2)
  )
  .enablePlugins(ProtobufPlugin)

lazy val client = project
  .configs(IntegrationTest, ClusterTest)
  .settings(commonSettings)
  .settings(inConfig(IntegrationTest)(Defaults.testTasks): _*)
  .settings(inConfig(ClusterTest)(Defaults.testTasks): _*)
  .settings(
    moduleName := "eventstore-client",
    testOptions in Test := Seq(Tests.Filter(_ endsWith "Spec")),
    testOptions in IntegrationTest := Seq(Tests.Filter(_ endsWith "ITest")),
    testOptions in ClusterTest := Seq(Tests.Filter(_ endsWith "CTest")),
    parallelExecution in IntegrationTest := false,
    parallelExecution in ClusterTest := false,
    coverageExcludedPackages := "eventstore.j;"
  )
  .settings(
    libraryDependencies ++= Seq(
      `ts-config`,`spray-json`, `scodec-bits`,
      Reactive.streams, Akka.actor, Akka.stream, AkkaHttp.http, AkkaHttp.`http-spray-json`
    ) ++ testDeps(
      specs2, Reactive.`streams-tck`, Akka.testkit, Akka.`stream-testkit`
    )
  )
  .dependsOn(core)

lazy val examples = project
  .settings(commonSettings)
  .settings(
    moduleName := "eventstore-client-examples",
    skip in publish := true,
    coverageExcludedPackages := "eventstore.examples;eventstore.j;"
  )
  .dependsOn(client)