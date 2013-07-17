import sbt._
import Keys._
import scalabuff.ScalaBuffPlugin._

object build extends Build {
  lazy val basicSettings = Seq(
    name := "eventstore-client",
    organization    := "com.eventstore",
    scalaVersion    := "2.10.2",
    scalacOptions   := Seq("-encoding", "UTF-8", "-unchecked", "-deprecation"),
    parallelExecution in Compile := true
  )

  lazy val root = Project("main", file("."), settings = basicSettings ++ Defaults.defaultSettings ++ scalabuffSettings).configs(ScalaBuff)
}