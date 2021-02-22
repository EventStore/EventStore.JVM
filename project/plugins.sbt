addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.6.4")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")

addSbtPlugin("com.github.sbt" % "sbt-release" % "1.0.15")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.5")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.9")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.7")

addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.16")

libraryDependencies += "com.github.os72" % "protoc-jar" % "3.7.0"