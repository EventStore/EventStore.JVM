addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.5")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.1.1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.9.2")
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.3.1")

addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.20")
addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.6.5")
libraryDependencies += "com.github.os72" % "protoc-jar" % "3.11.4"