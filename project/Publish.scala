import sbt._
import Keys._

object Publish {
  lazy val publishSetting = publishTo <<= version.apply {
    v =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("staging" at nexus + "service/local/staging/deploy/maven2")
  }

  lazy val settings = Seq(
    publishSetting,
    pomExtra := (
      <scm>
        <url>git@github.com:EventStore/eventstorejvmclient.git</url>
        <connection>scm:git:git@github.com:EventStore/eventstorejvmclient.git</connection>
      </scm>
        <developers>
          <developer>
            <id>t3hnar</id>
            <name>Yaroslav Klymko</name>
            <email>t3hnar@gmail.com</email>
          </developer>
        </developers>),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false })
}