import Resolvers._
import Dependencies._
import CompilerOptions._
import com.typesafe.sbt.packager.docker._
import scoverage.ScoverageKeys.{coverageFailOnMinimum, coverageMinimum}
import org.scalafmt.sbt.ScalafmtPlugin._
import microsites._
import ReleaseTransformations._

resolvers ++= additionalResolvers

lazy val IntegrationTest = config("it") extend Test

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
  scalaVersion := "2.12.8",
  organizationName := "Comcast Cable Communications Management, LLC",
  startYear := Some(2018),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  scalacOptions += "-target:jvm-1.8",
  scalacOptions ++= scalacOptionsByV(scalaVersion.value),
  scalacOptions in (Compile, doc) += "-no-link-warnings",
  // Use wart remover to eliminate code badness
  wartremoverErrors ++= Seq(
    Wart.EitherProjectionPartial,
    Wart.IsInstanceOf,
    Wart.JavaConversions,
    Wart.Return,
    Wart.LeakingSealed,
    Wart.ExplicitImplicitTypes
  ),

  // scala format
  scalafmtOnCompile := true,
  scalafmtOnCompile in IntegrationTest := true,

  // coverage options
  coverageMinimum := 85,
  coverageFailOnMinimum := true,
  coverageHighlighting := true,
)

lazy val testSettings = Seq(
  parallelExecution in Test := true,
  parallelExecution in IntegrationTest := false,
  fork in IntegrationTest := true,
  testOptions in Test += Tests.Argument("-oDNCXEPQRMIK"),
  logBuffered in Test := false,
  // Hide stack traces in tests
  traceLevel in Test := -1,
  traceLevel in IntegrationTest := -1
)

lazy val apiSettings = Seq(
  name := "api",
  libraryDependencies ++= apiDependencies ++ apiTestDependencies.map(_ % "test, it"),
  mainClass := Some("vinyldns.api.Boot"),
  javaOptions in reStart ++= Seq(
    "-Dlogback.configurationFile=test/logback.xml",
    s"""-Dvinyldns.base-version=${(version in ThisBuild).value}"""
  ),
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
  dockerBaseImage := "openjdk:8u191-jdk-alpine3.9",
  dockerUsername := Some("vinyldns"),
  packageName in Docker := "api",
  dockerExposedPorts := Seq(9000),
  dockerEntrypoint := Seq("/opt/docker/bin/api"),
  dockerExposedVolumes := Seq("/opt/docker/lib_extra"), // mount extra libs to the classpath
  dockerExposedVolumes := Seq("/opt/docker/conf"), // mount extra config to the classpath

  // add extra libs to class path via mount
  scriptClasspath in bashScriptDefines ~= (cp => cp :+ "/opt/docker/lib_extra/*"),

  // adds config file to mount
  bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
  bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml"""", // adds logback

  // this is the default version, can be overridden
  bashScriptExtraDefines += s"""addJava "-Dvinyldns.base-version=${(version in ThisBuild).value}"""",
  bashScriptExtraDefines += "(cd ${app_home} && ./wait-for-dependencies.sh && cd -)",
  credentials in Docker := Seq(Credentials(Path.userHome / ".ivy2" / ".dockerCredentials")),
  dockerCommands ++= Seq(
    Cmd("USER", "root"), // switch to root so we can install netcat
    ExecCmd("RUN", "apk", "add", "--update", "--no-cache", "netcat-openbsd", "bash"),
    Cmd("USER", "daemon") // switch back to the daemon user
  ),
  composeFile := baseDirectory.value.getAbsolutePath + "/../../docker/docker-compose.yml"
)

lazy val portalDockerSettings = Seq(
  dockerBaseImage := "openjdk:8u191-jdk-alpine3.9",
  dockerUsername := Some("vinyldns"),
  packageName in Docker := "portal",
  dockerExposedPorts := Seq(9001),
  dockerExposedVolumes := Seq("/opt/docker/lib_extra"), // mount extra libs to the classpath
  dockerExposedVolumes := Seq("/opt/docker/conf"), // mount extra config to the classpath

  // add extra libs to class path via mount
  scriptClasspath in bashScriptDefines ~= (cp => cp :+ "/opt/docker/lib_extra/*"),

  // adds config file to mount
  bashScriptExtraDefines += """addJava "-Dconfig.file=/opt/docker/conf/application.conf"""",
  bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=/opt/docker/conf/logback.xml"""",

  // this is the default version, can be overridden
  bashScriptExtraDefines += s"""addJava "-Dvinyldns.base-version=${(version in ThisBuild).value}"""",

  // wait for mysql
  bashScriptExtraDefines += "(cd /opt/docker/ && ./wait-for-dependencies.sh && cd -)",
  dockerCommands ++= Seq(
    Cmd("USER", "root"), // switch to root so we can install netcat
    ExecCmd("RUN", "apk", "add", "--update", "--no-cache", "netcat-openbsd", "bash"),
    Cmd("USER", "daemon") // switch back to the daemon user
  ),

  credentials in Docker := Seq(Credentials(Path.userHome / ".ivy2" / ".dockerCredentials"))
)

lazy val dynamoDBDockerSettings = Seq(
  composeFile := baseDirectory.value.getAbsolutePath + "/docker/docker-compose.yml"
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
  publish := (publish in Docker).value,
  // for sbt-native-packager (docker) to exclude local.conf
  mappings in Universal ~= ( _.filterNot {
    case (file, _) => file.getName.equals("local.conf")
  }),
  // for local.conf to be excluded in jars
  mappings in (Compile, packageBin) ~= ( _.filterNot {
    case (file, _) => file.getName.equals("local.conf")
  })
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
  .dependsOn(core % "compile->compile;test->test",
    dynamodb % "compile->compile;it->it",
    mysql % "compile->compile;it->it")
  .dependsOn(sqs % "compile->compile;it->it")

val killDocker = TaskKey[Unit]("killDocker", "Kills all vinyldns docker containers")
lazy val root = (project in file(".")).enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(headerSettings(IntegrationTest))
  .settings(sharedSettings)
  .settings(
    inConfig(IntegrationTest)(scalafmtConfigSettings),
    (scalastyleConfig in Test) := baseDirectory.value / "scalastyle-test-config.xml",
    (scalastyleConfig in IntegrationTest) := baseDirectory.value / "scalastyle-test-config.xml",
    killDocker := {
      import scala.sys.process._
      "./bin/remove-vinyl-containers.sh" !
    },
  )
  .aggregate(core, api, portal, dynamodb, mysql, sqs)

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
  homepage := Some(url("https://vinyldns.io")),
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
  sonatypeProfileName := "io.vinyldns"
)

lazy val core = (project in file("modules/core")).enablePlugins(AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(coreBuildSettings)
  .settings(corePublishSettings)
  .settings(testSettings)
  .settings(libraryDependencies ++= coreDependencies ++ commonTestDependencies.map(_ % "test"))
  .settings(scalaStyleCompile ++ scalaStyleTest)
  .settings(
    organization := "io.vinyldns"
  )

lazy val dynamodb = (project in file("modules/dynamodb"))
  .enablePlugins(DockerComposePlugin, AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(sharedSettings)
  .settings(headerSettings(IntegrationTest))
  .settings(inConfig(IntegrationTest)(scalafmtConfigSettings))
  .settings(corePublishSettings)
  .settings(testSettings)
  .settings(Defaults.itSettings)
  .settings(libraryDependencies ++= dynamoDBDependencies ++ commonTestDependencies.map(_ % "test, it"))
  .settings(scalaStyleCompile ++ scalaStyleTest)
  .settings(
    organization := "io.vinyldns",
    parallelExecution in Test := true,
    parallelExecution in IntegrationTest := true
  ).dependsOn(core % "compile->compile;test->test")
  .settings(name := "dynamodb")

lazy val mysql = (project in file("modules/mysql"))
  .enablePlugins(DockerComposePlugin, AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(sharedSettings)
  .settings(headerSettings(IntegrationTest))
  .settings(inConfig(IntegrationTest)(scalafmtConfigSettings))
  .settings(corePublishSettings)
  .settings(testSettings)
  .settings(Defaults.itSettings)
  .settings(libraryDependencies ++= mysqlDependencies ++ commonTestDependencies.map(_ % "test, it"))
  .settings(scalaStyleCompile ++ scalaStyleTest)
  .settings(
    organization := "io.vinyldns"
  ).dependsOn(core % "compile->compile;test->test")
  .settings(name := "mysql")

lazy val sqs = (project in file("modules/sqs"))
  .enablePlugins(DockerComposePlugin, AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(sharedSettings)
  .settings(headerSettings(IntegrationTest))
  .settings(inConfig(IntegrationTest)(scalafmtConfigSettings))
  .settings(corePublishSettings)
  .settings(testSettings)
  .settings(Defaults.itSettings)
  .settings(libraryDependencies ++= sqsDependencies ++ commonTestDependencies.map(_ % "test, it"))
  .settings(scalaStyleCompile ++ scalaStyleTest)
  .settings(
    organization := "io.vinyldns",
  ).dependsOn(core % "compile->compile;test->test")
  .settings(name := "sqs")

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
    coverageExcludedPackages := "<empty>;views.html.*;router.*",
    javaOptions in Test += "-Dconfig.file=conf/application-test.conf",
    
    // ads the version when working locally with sbt run
    PlayKeys.devSettings += "vinyldns.base-version" -> (version in ThisBuild).value,

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
  .dependsOn(dynamodb, mysql)

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
  fork in tut := true,
  micrositeEditButton := Some(MicrositeEditButton("Improve this page", "/edit/master/modules/docs/src/main/tut/{{ page.path }}")),
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.jpeg" | "*.gif" | "*.js" | "*.swf" | "*.md" | "*.webm" | "*.ico" | "CNAME" | "*.yml" | "*.svg" | "*.json" | "*.csv"
)

lazy val docs = (project in file("modules/docs"))
  .enablePlugins(MicrositesPlugin)
  .settings(docSettings)

// release stages

lazy val setSonatypeReleaseSettings = ReleaseStep(action = oldState => {
  // sonatype publish target, and sonatype release steps, are different if version is SNAPSHOT
  val extracted = Project.extract(oldState)
  val v = extracted.get(Keys.version)
  val snap = v.endsWith("SNAPSHOT")
  if (!snap) {
    val publishToSettings = Some("releases" at "https://oss.sonatype.org/" + "service/local/staging/deploy/maven2")
    val newState = extracted.appendWithSession(Seq(publishTo in core := publishToSettings), oldState)

    // create sonatypeReleaseCommand with releaseSonatype step
    val sonatypeCommand = Command.command("sonatypeReleaseCommand") {
      "project core" ::
      "publish" ::
      "sonatypeRelease" ::
      _
    }

    newState.copy(definedCommands = newState.definedCommands :+ sonatypeCommand)
  } else {
    val publishToSettings = Some("snapshots" at "https://oss.sonatype.org/" + "content/repositories/snapshots")
    val newState = extracted.appendWithSession(Seq(publishTo in core := publishToSettings), oldState)

    // create sonatypeReleaseCommand without releaseSonatype step
    val sonatypeCommand = Command.command("sonatypeReleaseCommand") {
      "project core" ::
        "publish" ::
        _
    }

    newState.copy(definedCommands = newState.definedCommands :+ sonatypeCommand)
  }
})

lazy val setDockerReleaseSettings = ReleaseStep(action = oldState => {
  // dockerUpdateLatest is set to true if the version is not a SNAPSHOT
  val extracted = Project.extract(oldState)
  val v = extracted.get(Keys.version)
  val snap = v.endsWith("SNAPSHOT")
  if (snap) extracted
      .appendWithSession(Seq(dockerUpdateLatest in ThisScope := true), oldState)
  else oldState
})

lazy val initReleaseStage = Seq[ReleaseStep](
  releaseStepCommand(";project root"), // use version.sbt file from root
  inquireVersions, // have a developer confirm versions
  setReleaseVersion,
  setDockerReleaseSettings,
  setSonatypeReleaseSettings
)

lazy val dockerPublishStage = Seq[ReleaseStep](
  releaseStepCommandAndRemaining(";project api;docker:publish"),
  releaseStepCommandAndRemaining(";project portal;docker:publish")
)

lazy val sonatypePublishStage = Seq[ReleaseStep](
  releaseStepCommandAndRemaining(";sonatypeReleaseCommand")
)

lazy val finalReleaseStage = Seq[ReleaseStep] (
  releaseStepCommand("project root"), // use version.sbt file from root
  commitReleaseVersion,
  setNextVersion,
  commitNextVersion
)

releaseProcess :=
  initReleaseStage ++
  dockerPublishStage ++
  sonatypePublishStage ++
  finalReleaseStage

// Let's do things in parallel!
addCommandAlias("validate", "; root/clean; " +
  "all core/headerCheck core/test:headerCheck " +
  "api/headerCheck api/test:headerCheck api/it:headerCheck " +
  "dynamodb/headerCheck dynamodb/test:headerCheck dynamodb/it:headerCheck " +
  "mysql/headerCheck mysql/test:headerCheck mysql/it:headerCheck " +
  "sqs/headerCheck sqs/test:headerCheck sqs/it:headerCheck " +
  "portal/headerCheck portal/test:headerCheck; " +
  "all core/scalastyle core/test:scalastyle " +
  "api/scalastyle api/test:scalastyle api/it:scalastyle " +
  "dynamodb/scalastyle dynamodb/test:scalastyle dynamodb/it:scalastyle" +
  "mysql/scalastyle mysql/test:scalastyle mysql/it:scalastyle" +
  "sqs/scalastyle sqs/test:scalastyle sqs/it:scalastyle" +
  "portal/scalastyle portal/test:scalastyle;" +
  "portal/createJsHeaders;portal/checkJsHeaders;" +
  "root/compile;root/test:compile;root/it:compile"
)

addCommandAlias("verify", "; project root; killDocker; " +
  "project api; dockerComposeUp; project dynamodb; dockerComposeUp; project mysql; dockerComposeUp; " +
  "project sqs; dockerComposeUp;" +
  "project root; coverage; " +
  "all core/test dynamodb/test mysql/test api/test dynamodb/it:test mysql/it:test api/it:test portal/test " +
  "sqs/test sqs/it:test; " +
  "project root; coverageReport; coverageAggregate; killDocker"
)

// Build the artifacts for release
addCommandAlias("build-api", ";project api;clean;assembly")
addCommandAlias("build-portal", ";project portal;clean;preparePortal;dist")
addCommandAlias("build", ";build-api;build-portal")
