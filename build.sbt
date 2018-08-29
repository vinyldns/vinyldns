import Resolvers._
import Dependencies._
import CompilerOptions._
import com.typesafe.sbt.packager.docker._
import scoverage.ScoverageKeys.{coverageFailOnMinimum, coverageMinimum}
import org.scalafmt.sbt.ScalafmtPlugin._
import microsites._
import ReleaseTransformations._

resolvers ++= additionalResolvers

lazy val IntegrationTest = config("it") extend(Test)

// Needed because we want scalastyle for integration tests which is not first class
val codeStyleIntegrationTest = taskKey[Unit]("enforce code style then integration test")
def scalaStyleIntegrationTest: Seq[Def.Setting[_]] = {
  inConfig(IntegrationTest)(ScalastylePlugin.rawScalastyleSettings()) ++
    Seq(
      scalastyleConfig in IntegrationTest := root.base / "scalastyle-test-config.xml",
      scalastyleTarget in IntegrationTest := target.value / "scalastyle-it-results.xml",
      scalastyleFailOnError in IntegrationTest := (scalastyleFailOnError in scalastyle).value,
      (scalastyleFailOnWarning in IntegrationTest) := (scalastyleFailOnWarning in scalastyle).value,
      scalastyleSources in IntegrationTest := (unmanagedSourceDirectories in IntegrationTest).value,
      codeStyleIntegrationTest := scalastyle.in(IntegrationTest).toTask("").value
    )
}

// Create a default Scala style task to run with tests
lazy val testScalastyle = taskKey[Unit]("testScalastyle")
def scalaStyleTest: Seq[Def.Setting[_]] = Seq(
  (scalastyleConfig in Test) := baseDirectory.value / ".." / ".." / "scalastyle-test-config.xml",
  scalastyleTarget in Test := target.value / "scalastyle-test-results.xml",
  scalastyleFailOnError in Test := (scalastyleFailOnError in scalastyle).value,
  (scalastyleFailOnWarning in Test) := (scalastyleFailOnWarning in scalastyle).value,
  scalastyleSources in Test := (unmanagedSourceDirectories in Test).value,
  testScalastyle := scalastyle.in(Test).toTask("").value
)

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")
def scalaStyleCompile: Seq[Def.Setting[_]] = Seq(
  compileScalastyle := scalastyle.in(Compile).toTask("").value
)

def scalaStyleSettings: Seq[Def.Setting[_]] = scalaStyleCompile ++ scalaStyleTest ++ scalaStyleIntegrationTest

// settings that should be inherited by all projects
lazy val sharedSettings = Seq(
  organization := "vinyldns",
  scalaVersion := "2.12.6",
  organizationName := "Comcast Cable Communications Management, LLC",
  startYear := Some(2018),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  scalacOptions += "-target:jvm-1.8",
  scalacOptions ++= scalacOptionsByV(scalaVersion.value),
  scalacOptions in (Compile, doc) += "-no-link-warnings",
  // Use wart remover to eliminate code badness
  wartremoverErrors ++= Seq(
    Wart.ArrayEquals,
    Wart.EitherProjectionPartial,
    Wart.IsInstanceOf,
    Wart.JavaConversions,
    Wart.Return,
    Wart.LeakingSealed,
    Wart.ExplicitImplicitTypes
  ),

  // scala format
  scalafmtOnCompile := true,
  scalafmtOnCompile in IntegrationTest := true
)

lazy val testSettings = Seq(
  parallelExecution in Test := false,
  parallelExecution in IntegrationTest := false,
  fork in IntegrationTest := false,
  testOptions in Test += Tests.Argument("-oD"),
  logBuffered in Test := false
)

lazy val apiSettings = Seq(
  name := "api",
  libraryDependencies ++= compileDependencies ++ testDependencies.map(_ % "test, it"),
  mainClass := Some("vinyldns.api.Boot"),
  javaOptions in reStart += "-Dlogback.configurationFile=test/logback.xml",
  coverageMinimum := 85,
  coverageFailOnMinimum := true,
  coverageHighlighting := true,
  coverageExcludedPackages := ".*Boot.*"
)

lazy val apiAssemblySettings = Seq(
  assemblyJarName in assembly := "vinyldns.jar",
  test in assembly := {},
  mainClass in assembly := Some("vinyldns.api.Boot"),
  mainClass in reStart := Some("vinyldns.api.Boot"),
  // there are some odd things from dnsjava including update.java and dig.java that we don't use
  assemblyMergeStrategy in assembly := {
    case "update.class"| "dig.class" => MergeStrategy.discard
    case PathList("scala", "tools", "nsc", "doc", "html", "resource", "lib", "index.js") => MergeStrategy.discard
    case PathList("scala", "tools", "nsc", "doc", "html", "resource", "lib", "template.js") => MergeStrategy.discard
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

lazy val apiDockerSettings = Seq(
  dockerBaseImage := "openjdk:8u171-jdk",
  dockerUsername := Some("vinyldns"),
  packageName in Docker := "api",
  dockerUpdateLatest := true,
  dockerExposedPorts := Seq(9000),
  dockerEntrypoint := Seq("/opt/docker/bin/api"),
  dockerExposedVolumes := Seq("/opt/docker/lib_extra"), // mount extra libs to the classpath
  dockerExposedVolumes := Seq("/opt/docker/conf"), // mount extra config to the classpath

  // add extra libs to class path via mount
  scriptClasspath in bashScriptDefines ~= (cp => cp :+ "/opt/docker/lib_extra/*"),

  // adds config file to mount
  bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
  bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml"""", // adds logback
  bashScriptExtraDefines += "(cd ${app_home} && ./wait-for-dependencies.sh && cd -)",
  credentials in Docker := Seq(Credentials(Path.userHome / ".ivy2" / ".dockerCredentials")),
  dockerCommands ++= Seq(
    Cmd("USER", "root"), // switch to root so we can install netcat
    ExecCmd("RUN", "apt-get", "update"),
    ExecCmd("RUN", "apt-get", "install", "-y", "netcat-openbsd"),
    Cmd("USER", "daemon") // switch back to the daemon user
  ),
  composeFile := baseDirectory.value.getAbsolutePath + "/../../docker/docker-compose.yml"
)

lazy val portalDockerSettings = Seq(
  dockerBaseImage := "openjdk:8u171-jdk",
  dockerUsername := Some("vinyldns"),
  packageName in Docker := "portal",
  dockerUpdateLatest := true,
  dockerExposedPorts := Seq(9001),
  dockerExposedVolumes := Seq("/opt/docker/lib_extra"), // mount extra libs to the classpath
  dockerExposedVolumes := Seq("/opt/docker/conf"), // mount extra config to the classpath

  // add extra libs to class path via mount
  scriptClasspath in bashScriptDefines ~= (cp => cp :+ "/opt/docker/lib_extra/*"),

  // adds config file to mount
  bashScriptExtraDefines += """addJava "-Dconfig.file=/opt/docker/conf/application.conf"""",
  bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=/opt/docker/conf/logback.xml"""",
  credentials in Docker := Seq(Credentials(Path.userHome / ".ivy2" / ".dockerCredentials"))
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val apiPublishSettings = Seq(
  publishArtifact := false,
  publishLocal := (publishLocal in Docker).value,
  publish := (publish in Docker).value
)

lazy val portalPublishSettings = Seq(
  publishArtifact := false,
  publishLocal := (publishLocal in Docker).value,
  publish := (publish in Docker).value
)

lazy val pbSettings = Seq(
  PB.targets in Compile := Seq(
    PB.gens.java("2.6.1") -> (sourceManaged in Compile).value
  ),
  PB.protocVersion := "-v261"
)

lazy val allApiSettings = Revolver.settings ++ Defaults.itSettings ++
  apiSettings ++
  sharedSettings ++
  apiAssemblySettings ++
  testSettings ++
  apiPublishSettings ++
  apiDockerSettings ++
  scalaStyleSettings

lazy val api = (project in file("modules/api"))
  .enablePlugins(JavaAppPackaging, DockerComposePlugin, AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(allApiSettings)
  .settings(headerSettings(IntegrationTest))
  .settings(inConfig(IntegrationTest)(scalafmtConfigSettings))
  .dependsOn(core)

lazy val root = (project in file(".")).enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(headerSettings(IntegrationTest))
  .settings(sharedSettings)
  .settings(
    inConfig(IntegrationTest)(scalafmtConfigSettings),
    (scalastyleConfig in Test) := baseDirectory.value / "scalastyle-test-config.xml",
    (scalastyleConfig in IntegrationTest) := baseDirectory.value / "scalastyle-test-config.xml"
  )
  .aggregate(core, api, portal)

lazy val coreBuildSettings = Seq(
  name := "core",

  // do not use unused params as NoOpCrypto ignores its constructor, we should provide a way
  // to write a crypto plugin so that we fall back to a noarg constructor
  scalacOptions ++= scalacOptionsByV(scalaVersion.value).filterNot(_ == "-Ywarn-unused:params")
) ++ pbSettings

import xerial.sbt.Sonatype._
lazy val corePublishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  autoAPIMappings := true,
  publish in Docker := {},
  mainClass := None,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  homepage := Some(url("http://vinyldns.io")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/vinyldns/vinyldns"),
      "scm:git@github.com:vinyldns/vinyldns.git"
    )
  ),
  developers := List(
    Developer(id="pauljamescleary", name="Paul James Cleary", email="pauljamescleary@gmail.com", url=url("https://github.com/pauljamescleary")),
    Developer(id="rebstar6", name="Rebecca Star", email="rebstar6@gmail.com", url=url("https://github.com/rebstar6")),
    Developer(id="nimaeskandary", name="Nima Eskandary", email="nimaesk1@gmail.com", url=url("https://github.com/nimaeskandary")),
    Developer(id="mitruly", name="Michael Ly", email="michaeltrulyng@gmail.com", url=url("https://github.com/mitruly")),
    Developer(id="britneywright", name="Britney Wright", email="blw06g@gmail.com", url=url("https://github.com/britneywright")),
  ),
  sonatypeProfileName := "io.vinyldns",
  credentials += Credentials(Path.userHome / ".sbt" / "1.0" / "vinyldns-gpg-credentials")
)

lazy val core = (project in file("modules/core")).enablePlugins(AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(coreBuildSettings)
  .settings(corePublishSettings)
  .settings(testSettings)
  .settings(libraryDependencies ++= coreDependencies ++ coreTestDependencies.map(_ % "test"))
  .settings(scalaStyleCompile ++ scalaStyleTest)
  .settings(
    organization := "io.vinyldns",
    coverageMinimum := 85,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )

val preparePortal = TaskKey[Unit]("preparePortal", "Runs NPM to prepare portal for start")
val checkJsHeaders = TaskKey[Unit]("checkJsHeaders", "Runs script to check for APL 2.0 license headers")
val createJsHeaders = TaskKey[Unit]("createJsHeaders", "Runs script to prepend APL 2.0 license headers to files")

lazy val portal = (project in file("modules/portal")).enablePlugins(PlayScala, AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(testSettings)
  .settings(portalPublishSettings)
  .settings(portalDockerSettings)
  .settings(
    name := "portal",
    libraryDependencies ++= portalDependencies,
    routesGenerator := InjectedRoutesGenerator,
    coverageMinimum := 75,
    coverageExcludedPackages := "<empty>;views.html.*;router.*",
    javaOptions in Test += "-Dconfig.file=conf/application-test.conf",
    javaOptions in run += "-Dhttp.port=9001 -Dconfig.file=modules/portal/conf/application.conf",

    // adds an extra classpath to the portal loading so we can externalize jars, make sure to create the lib_extra
    // directory and lay down any dependencies that are required when deploying
    scriptClasspath in bashScriptDefines ~= (cp => cp :+ "lib_extra/*"),
    mainClass in reStart := None,

    // we need to filter out unused for the portal as the play framework needs a lot of unused things
    scalacOptions ~= { opts => opts.filterNot(p => p.contains("unused")) },

    // runs our prepare portal process
    preparePortal := {
      import scala.sys.process._
      "./modules/portal/prepare-portal.sh" !
    },

    checkJsHeaders := {
      import scala.sys.process._
      "./bin/add-license-headers.sh -d=modules/portal/public/lib -f=js -c" !
    },

    createJsHeaders := {
      import scala.sys.process._
      "./bin/add-license-headers.sh -d=modules/portal/public/lib -f=js" !
    },

    // change the name of the output to portal.zip
    packageName in Universal := "portal"
  )
  .dependsOn(core)

lazy val docSettings = Seq(
  git.remoteRepo := "https://github.com/vinyldns/vinyldns",
  micrositeGithubOwner := "VinylDNS",
  micrositeGithubRepo := "vinyldns",
  micrositeName := "VinylDNS",
  micrositeDescription := "DNS Management Platform",
  micrositeAuthor := "VinylDNS",
  micrositeHomepage := "http://vinyldns.io",
  micrositeDocumentationUrl := "/api",
  micrositeGitterChannelUrl := "vinyldns/Lobby",
  micrositeDocumentationLabelDescription := "API Documentation",
  micrositeShareOnSocial := false,
  micrositeExtraMdFiles := Map(
    file("CONTRIBUTING.md") -> ExtraMdFileConfig(
      "contributing.md",
      "page",
      Map("title" -> "Contributing", "section" -> "contributing", "position" -> "4")
    )
  ),
  micrositePushSiteWith := GitHub4s,
  micrositeGithubToken := sys.env.get("SBT_MICROSITES_PUBLISH_TOKEN"),
  ghpagesNoJekyll := false,
  fork in tut := true
)

lazy val docs = (project in file("modules/docs"))
  .enablePlugins(MicrositesPlugin)
  .settings(docSettings)

// release stages

lazy val initReleaseStage = Seq[ReleaseStep](
  releaseStepCommand("project root"), // use version.sbt file from root
  inquireVersions, // have a developer confirm versions
  setReleaseVersion // set version in context
)

lazy val validateVerifyReleaseStage = Seq[ReleaseStep](
  releaseStepCommand("project root"),
  releaseStepCommandAndRemaining("validate"),
  releaseStepCommandAndRemaining("verify"),
)

lazy val dockerReleaseStage = Seq[ReleaseStep](
  releaseStepCommandAndRemaining(";project api;docker:publish"), // publish api and portal images to Docker Hub
  releaseStepCommandAndRemaining(";project portal;docker:publish"),
)

lazy val sonatypePublishStage = Seq[ReleaseStep] (
  releaseStepCommandAndRemaining(";project core;publish") // only releases to sonatype staging repo
)

lazy val sonatypeReleaseStage = if (versionString.endsWith("SNAPSHOT")) {
  Seq[ReleaseStep](releaseStepCommandAndRemaining(";project core;sonatypeRelease")) // closes staging repo and promotes
} else {
  Seq[ReleaseStep]() // skip stage if snapshot
}

lazy val finalReleaseStage = Seq[ReleaseStep] (
  releaseStepCommand("project root"), // use version.sbt file from root
  tagRelease, // tag latest commit with published version
  setNextVersion,
  commitNextVersion // commit new version.sbt
)

releaseProcess :=
  initReleaseStage ++
  validateVerifyReleaseStage ++
  dockerReleaseStage ++
  sonatypePublishStage ++
  sonatypeReleaseStage ++
  finalReleaseStage

// Validate runs static checks and compile to make sure we can go
addCommandAlias("validate-api",
  ";project api; clean; headerCheck; test:headerCheck; it:headerCheck; scalastyle; test:scalastyle; " +
    "it:scalastyle; compile; test:compile; it:compile")
addCommandAlias("validate-core",
  ";project core; clean; headerCheck; test:headerCheck; scalastyle; test:scalastyle; compile; test:compile")
addCommandAlias("validate-portal",
  ";project portal; clean; headerCheck; test:headerCheck; compile; test:compile; createJsHeaders; checkJsHeaders")
addCommandAlias("validate", ";validate-core;validate-api;validate-portal")

// Verify runs all tests and code coverage
addCommandAlias("verify",
  ";project api;dockerComposeUp;project root;coverage;test;it:test;coverageReport;coverageAggregate;project api;dockerComposeStop")

// Build the artifacts for release
addCommandAlias("build-api", ";project api;clean;assembly")
addCommandAlias("build-portal", ";project portal;clean;preparePortal;dist")
addCommandAlias("build", ";build-api;build-portal")
