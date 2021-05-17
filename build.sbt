import sbtprotobuf.ProtobufPlugin
import com.github.os72.protocjar.Protoc
import Dependencies._

lazy val commonSettings = Seq(

  organization         := "com.geteventstore",
  scalaVersion         := crossScalaVersions.value.head,
  crossScalaVersions   := Seq("2.13.6", "2.12.13"),
  releaseCrossBuild    := true,
  licenses             := Seq("BSD 3-Clause" -> url("http://raw.github.com/EventStore/EventStore.JVM/master/LICENSE")),
  homepage             := Some(new URL("http://github.com/EventStore/EventStore.JVM")),
  organizationHomepage := Some(new URL("http://geteventstore.com")),
  description          := "Event Store JVM Client",
  startYear            := Some(2013),
  scalacOptions       ++= Seq("-target:jvm-1.8"),
  javacOptions        ++= Seq("-target", "8", "-source", "8"),  
  Test / compile / scalacOptions --= Seq("-Ywarn-value-discard", "-Wvalue-discard"),
  Compile / doc / scalacOptions ++= Seq("-groups", "-implicits", "-no-link-warnings"),
  Compile / doc / scalacOptions --= Seq("-Xfatal-warnings"),

  Global / pomExtra := {
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

def priorTo2_13(scalaVersion: String): Boolean =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, minor)) if minor < 13 => true
    case _                              => false
  }

///

lazy val IntegrationTest = config("it") extend Test
lazy val ClusterTest     = config("c") extend Test

lazy val root = project.in(file("."))
  .settings(commonSettings)
  .settings(publish / skip := true)
  .dependsOn(core, client, examples)
  .aggregate(core, client, examples)

lazy val core = project
  .settings(commonSettings)
  .settings(
    moduleName := "eventstore-client-core",
    ProtobufConfig / version := protobufVersion,
    ProtobufConfig / protobufRunProtoc := { args => Protoc.runProtoc(s"-v$protobufVersion" +: args.toArray) },
    coverageExcludedPackages :=
      "eventstore.proto;" +
      "eventstore.tcp.EventStoreProtoFormats;" +
      "eventstore.tcp.MarkerBytes;",
    Compile / unmanagedSourceDirectories += {
      (Compile / sourceDirectory).value / (if(priorTo2_13(scalaVersion.value)) "scala-2.12" else "scala-2.13")
    }
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
    Test            / testOptions := Seq(Tests.Filter(_ endsWith "Spec")),
    IntegrationTest / testOptions := Seq(Tests.Filter(_ endsWith "ITest")),
    ClusterTest     / testOptions := Seq(Tests.Filter(_ endsWith "CTest")),
    IntegrationTest / parallelExecution := false,
    ClusterTest     / parallelExecution := false,
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
    scalacOptions --= Seq("-Ywarn-value-discard", "-Wvalue-discard"),
    publish / skip := true,
    coverageExcludedPackages := "eventstore.examples;eventstore.j;"
  )
  .dependsOn(client)