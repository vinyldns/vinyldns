logLevel := Level.Warn

resolvers += "Flyway" at "https://flywaydb.org/repo"

resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")

addSbtPlugin("io.github.davidmweber" % "flyway-sbt" % "5.0.0")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.3.7")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.5")

addSbtPlugin("com.tapad" % "sbt-docker-compose" % "1.0.34")

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.2")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.8")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.0.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.15")

addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-license-report" % "1.2.0")

addSbtPlugin("com.47deg"  % "sbt-microsites" % "0.7.24")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")

addSbtPlugin("io.crashbox" % "sbt-gpg" % "0.2.0")

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.3")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.26")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.14.0")

addSbtPlugin("ch.epfl.scala" % "sbt-web-scalajs-bundler" % "0.14.0")