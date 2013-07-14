import sbt._
import Keys._
import scalabuff.ScalaBuffPlugin._

object build extends Build {

  val depsRepos = Seq(
    "TnmPublic"  at "http://nexus.thenewmotion.com/content/groups/public",
    "Releases"   at "http://nexus.thenewmotion.com/content/repositories/releases",
    "Snapshots"  at "http://nexus.thenewmotion.com/content/repositories/snapshots",
    "spray repo" at "http://repo.spray.io"
  )

  lazy val basicSettings = Seq(
    name := "eventstore-client",
    organization    := "com.geteventstore",
    scalaVersion    := "2.10.2",
    resolvers       ++= depsRepos,
    scalacOptions   := Seq(
      "-encoding", "UTF-8",
      "-unchecked",
      "-deprecation"),
    parallelExecution in Compile := true
  )

  lazy val root = Project("main", file("."), settings = basicSettings ++ Defaults.defaultSettings ++ scalabuffSettings).configs(ScalaBuff)
}