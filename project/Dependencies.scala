import sbt._

object Dependencies {

  val protobufVersion = "3.17.3"
  
  val `ts-config`   = "com.typesafe" %  "config"      % "1.4.1"
  val `scodec-bits` = "org.scodec"   %% "scodec-bits" % "1.1.24"
  val `spray-json`  = "io.spray"     %% "spray-json"  % "1.3.6"
  val specs2        = "org.specs2"   %% "specs2-core" % "4.10.6"

  ///

  object Akka {
    private val version = "2.6.12"
    val actor            = "com.typesafe.akka" %% "akka-actor"          % version
    val stream           = "com.typesafe.akka" %% "akka-stream"         % version
    val testkit          = "com.typesafe.akka" %% "akka-testkit"        % version
    val `stream-testkit` = "com.typesafe.akka" %% "akka-stream-testkit" % version
  }

  object AkkaHttp {
    private val version = "10.2.3"
    val http              = "com.typesafe.akka" %% "akka-http"            % version
    val `http-spray-json` = "com.typesafe.akka" %% "akka-http-spray-json" % version
  }

  object Reactive {
    private val version = "1.0.3"
    val streams       = "org.reactivestreams" % "reactive-streams"     % version
    val `streams-tck` = "org.reactivestreams" % "reactive-streams-tck" % version
  }


  def testDeps(mids: ModuleID*): Seq[ModuleID] = mids.map(_ % Test)

}
