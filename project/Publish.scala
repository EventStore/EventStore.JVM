import sbt._
import Keys._
import xerial.sbt.Sonatype.sonatypeSettings

object Publish {
  lazy val settings = sonatypeSettings :+ (pomExtra :=
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
    </developers>)
}