import sbt._
import Keys._
import scalabuff.ScalaBuffPlugin._
import com.typesafe.sbt.SbtScalariform.scalariformSettings

object build extends Build {
  val akkaVersion = "2.2.0"
  val akka = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val scalabuff = "net.sandrogrzicic" %% "scalabuff-runtime" % "1.3.1"
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
  val junit = "junit" % "junit" % "4.11" % "test"
  val specs2 = "org.specs2" %% "specs2" % "2.1.1" % "test"
  val mockito = "org.mockito" % "mockito-all" % "1.9.5" % "test"

  lazy val basicSettings = Seq(
    name            := "eventstore-client",
    organization    := "com.geteventstore",
    version         := "0.1-SNAPSHOT",
    scalaVersion    := "2.10.2",
    scalacOptions   := Seq("-encoding", "UTF-8", "-unchecked", "-deprecation"),
    libraryDependencies ++= Seq(akka, akkaTestkit, scalabuff, junit, specs2, mockito),
    resolvers := Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/")
  )

  def itFilter(name: String): Boolean = name endsWith "ITest"
  def specFilter(name: String): Boolean = name endsWith "Spec"

  lazy val IntegrationTest = config("it") extend Test

  lazy val root = Project("main", file("."), settings = basicSettings ++ Defaults.defaultSettings ++ scalabuffSettings ++ scalariformSettings)
    .configs(ScalaBuff, IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.testTasks): _*)
    .settings(
    testOptions in Test := Seq(Tests.Filter(specFilter)),
    testOptions in IntegrationTest := Seq(Tests.Filter(itFilter)),
    parallelExecution in IntegrationTest := false,
    fork in IntegrationTest := true
  )
}