import sbt._

object Dependencies {

  val protobufVersion = "3.19.2"
  val circeVersion    = "0.14.1"
  
  val `ts-config`   = "com.typesafe" %  "config"      % "1.4.1"
  val `scodec-bits` = "org.scodec"   %% "scodec-bits" % "1.1.30"
  val circe         = "io.circe" %% "circe-core"      % circeVersion

  // Testing

  val circeParser   = "io.circe"   %% "circe-parser" % circeVersion
  val specs2        = "org.specs2" %% "specs2-core"  % "4.13.3"

  ///

  object Akka {
    private val version = "2.6.18"
    val actor            = "com.typesafe.akka" %% "akka-actor"          % version
    val stream           = "com.typesafe.akka" %% "akka-stream"         % version
    val testkit          = "com.typesafe.akka" %% "akka-testkit"        % version
    val `stream-testkit` = "com.typesafe.akka" %% "akka-stream-testkit" % version
  }

  object Sttp {
    private val version = "3.3.18"
    val sttpCore      = "com.softwaremill.sttp.client3" %% "core"           % version
    val sttpOkHttp    = "com.softwaremill.sttp.client3" %% "okhttp-backend" % version
    val sttpCirce     = "com.softwaremill.sttp.client3" %% "circe"          % version
  }

  object Reactive {
    private val version = "1.0.3"
    val streams       = "org.reactivestreams" % "reactive-streams"     % version
    val `streams-tck` = "org.reactivestreams" % "reactive-streams-tck" % version
  }


  def testDeps(mids: ModuleID*): Seq[ModuleID] = mids.map(_ % Test)

}
