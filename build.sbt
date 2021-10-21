import CompilerOptions._
import Dependencies._
import Resolvers._
import microsites._
import org.scalafmt.sbt.ScalafmtPlugin._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import scoverage.ScoverageKeys.{coverageFailOnMinimum, coverageMinimum}

import scala.util.Try

resolvers ++= additionalResolvers

lazy val IntegrationTest = config("it").extend(Test)

// settings that should be inherited by all projects
lazy val sharedSettings = Seq(
  organization := "vinyldns",
  scalaVersion := "2.12.11",
  organizationName := "Comcast Cable Communications Management, LLC",
  startYear := Some(2018),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  scalacOptions ++= scalacOptionsByV(scalaVersion.value),
  scalacOptions in (Compile, doc) += "-no-link-warnings",
  // Use wart remover to eliminate code badness
  wartremoverErrors := (
    if (getPropertyFlagOrDefault("build.lintOnCompile", true))
      Seq(
        Wart.EitherProjectionPartial,
        Wart.IsInstanceOf,
        Wart.JavaConversions,
        Wart.Return,
        Wart.LeakingSealed,
        Wart.ExplicitImplicitTypes
      )
    else Seq.empty
  ),
  // scala format
  scalafmtOnCompile := getPropertyFlagOrDefault("build.scalafmtOnCompile", false),
  // coverage options
  coverageMinimum := 85,
  coverageFailOnMinimum := true,
  coverageHighlighting := true
)

lazy val testSettings = Seq(
  parallelExecution in Test := true,
  parallelExecution in IntegrationTest := false,
  fork in IntegrationTest := true,
  testOptions in Test += Tests.Argument("-oDNCXEPQRMIK", "-l", "SkipCI"),
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
  coverageExcludedPackages := "Boot.*"
)

lazy val apiAssemblySettings = Seq(
  assemblyOutputPath in assembly := file("assembly/vinyldns.jar"),
  test in assembly := {},
  mainClass in assembly := Some("vinyldns.api.Boot"),
  mainClass in reStart := Some("vinyldns.api.Boot"),
  assemblyMergeStrategy in assembly := {
    case PathList("scala", "tools", "nsc", "doc", "html", "resource", "lib", "index.js") =>
      MergeStrategy.discard
    case PathList("scala", "tools", "nsc", "doc", "html", "resource", "lib", "template.js") =>
      MergeStrategy.discard
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
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
  mappings in Universal ~= (_.filterNot {
    case (file, _) => file.getName.equals("local.conf")
  }),
  // for local.conf to be excluded in jars
  mappings in (Compile, packageBin) ~= (_.filterNot {
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
  apiPublishSettings

lazy val api = (project in file("modules/api"))
  .enablePlugins(JavaAppPackaging, AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(allApiSettings)
  .settings(headerSettings(IntegrationTest))
  .settings(inConfig(IntegrationTest)(scalafmtConfigSettings))
  .dependsOn(
    core % "compile->compile;test->test",
    mysql % "compile->compile;it->it",
    sqs % "compile->compile;it->it",
    r53 % "compile->compile;it->it"
  )

lazy val root = (project in file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(headerSettings(IntegrationTest))
  .settings(sharedSettings)
  .settings(
    inConfig(IntegrationTest)(scalafmtConfigSettings)
  )
  .aggregate(core, api, portal, mysql, sqs, r53)

lazy val coreBuildSettings = Seq(
  name := "core",
  // do not use unused params as NoOpCrypto ignores its constructor, we should provide a way
  // to write a crypto plugin so that we fall back to a noarg constructor
  scalacOptions ++= scalacOptionsByV(scalaVersion.value).filterNot(_ == "-Ywarn-unused:params")
) ++ pbSettings

lazy val corePublishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ =>
    false
  },
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
  sonatypeProfileName := "io.vinyldns"
)

lazy val core = (project in file("modules/core"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(coreBuildSettings)
  .settings(corePublishSettings)
  .settings(testSettings)
  .settings(libraryDependencies ++= coreDependencies ++ commonTestDependencies.map(_ % "test"))
  .settings(
    organization := "io.vinyldns"
  )

lazy val mysql = (project in file("modules/mysql"))
  .enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(sharedSettings)
  .settings(headerSettings(IntegrationTest))
  .settings(inConfig(IntegrationTest)(scalafmtConfigSettings))
  .settings(corePublishSettings)
  .settings(testSettings)
  .settings(Defaults.itSettings)
  .settings(libraryDependencies ++= mysqlDependencies ++ commonTestDependencies.map(_ % "test, it"))
  .settings(
    organization := "io.vinyldns"
  )
  .dependsOn(core % "compile->compile;test->test")
  .settings(name := "mysql")

lazy val sqs = (project in file("modules/sqs"))
  .enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(sharedSettings)
  .settings(headerSettings(IntegrationTest))
  .settings(inConfig(IntegrationTest)(scalafmtConfigSettings))
  .settings(corePublishSettings)
  .settings(testSettings)
  .settings(Defaults.itSettings)
  .settings(libraryDependencies ++= sqsDependencies ++ commonTestDependencies.map(_ % "test, it"))
  .settings(
    organization := "io.vinyldns"
  )
  .dependsOn(core % "compile->compile;test->test")
  .settings(name := "sqs")

lazy val r53 = (project in file("modules/r53"))
  .enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(sharedSettings)
  .settings(headerSettings(IntegrationTest))
  .settings(inConfig(IntegrationTest)(scalafmtConfigSettings))
  .settings(corePublishSettings)
  .settings(testSettings)
  .settings(Defaults.itSettings)
  .settings(libraryDependencies ++= r53Dependencies ++ commonTestDependencies.map(_ % "test, it"))
  .settings(
    organization := "io.vinyldns",
    coverageMinimum := 65
  )
  .dependsOn(core % "compile->compile;test->test")
  .settings(name := "r53")

val preparePortal = TaskKey[Unit]("preparePortal", "Runs NPM to prepare portal for start")
val checkJsHeaders =
  TaskKey[Unit]("checkJsHeaders", "Runs script to check for APL 2.0 license headers")
val createJsHeaders =
  TaskKey[Unit]("createJsHeaders", "Runs script to prepend APL 2.0 license headers to files")

lazy val portal = (project in file("modules/portal"))
  .enablePlugins(PlayScala, AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(testSettings)
  .settings(portalPublishSettings)
  .settings(
    name := "portal",
    libraryDependencies ++= portalDependencies,
    routesGenerator := InjectedRoutesGenerator,
    coverageExcludedPackages := "<empty>;views.html.*;router.*;controllers\\.javascript.*;.*Reverse.*",
    javaOptions in Test += "-Dconfig.file=conf/application-test.conf",
    // ads the version when working locally with sbt run
    PlayKeys.devSettings += "vinyldns.base-version" -> (version in ThisBuild).value,
    // adds an extra classpath to the portal loading so we can externalize jars, make sure to create the lib_extra
    // directory and lay down any dependencies that are required when deploying
    scriptClasspath in bashScriptDefines ~= (cp => cp :+ "lib_extra/*"),
    mainClass in reStart := None,
    // we need to filter out unused for the portal as the play framework needs a lot of unused things
    scalacOptions ~= { opts =>
      opts.filterNot(p => p.contains("unused"))
    },
    // runs our prepare portal process
    preparePortal := {
      import scala.sys.process._
      "./modules/portal/prepare-portal.sh" !
    },
    checkJsHeaders := {
      import scala.sys.process._
      "./utils/add-license-headers.sh -d=modules/portal/public/lib -f=js -c" !
    },
    createJsHeaders := {
      import scala.sys.process._
      "./utils/add-license-headers.sh -d=modules/portal/public/lib -f=js" !
    },
    // change the name of the output to portal.zip
    packageName in Universal := "portal"
  )
  .dependsOn(mysql)

lazy val docSettings = Seq(
  git.remoteRepo := "https://github.com/vinyldns/vinyldns",
  micrositeGithubOwner := "vinyldns",
  micrositeGithubRepo := "vinyldns",
  micrositeName := "VinylDNS",
  micrositeDescription := "DNS Automation and Governance",
  micrositeAuthor := "VinylDNS",
  micrositeHomepage := "https://vinyldns.io",
  micrositeDocumentationUrl := "/api",
  micrositeDocumentationLabelDescription := "API Documentation",
  micrositeHighlightLanguages ++= Seq("json", "yaml", "bnf", "plaintext"),
  micrositeGitterChannel := false,
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
  fork in mdoc := true,
  mdocIn := (sourceDirectory in Compile).value / "mdoc",
  micrositeFavicons := Seq(
    MicrositeFavicon("favicon16x16.png", "16x16"),
    MicrositeFavicon("favicon32x32.png", "32x32")
  ),
  micrositeEditButton := Some(
    MicrositeEditButton(
      "Improve this page",
      "/edit/master/modules/docs/src/main/mdoc/{{ page.path }}"
    )
  ),
  micrositeFooterText := None,
  micrositeHighlightTheme := "hybrid",
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.jpeg" | "*.gif" | "*.js" | "*.swf" | "*.md" | "*.webm" | "*.ico" | "CNAME" | "*.yml" | "*.svg" | "*.json" | "*.csv"
)

lazy val docs = (project in file("modules/docs"))
  .enablePlugins(MicrositesPlugin, MdocPlugin)
  .settings(docSettings)

// release stages

lazy val setSonatypeReleaseSettings = ReleaseStep(action = oldState => {
  // sonatype publish target, and sonatype release steps, are different if version is SNAPSHOT
  val extracted = Project.extract(oldState)
  val v = extracted.get(Keys.version)
  val snap = v.endsWith("SNAPSHOT")
  if (!snap) {
    val publishToSettings =
      Some("releases".at("https://oss.sonatype.org/" + "service/local/staging/deploy/maven2"))
    val newState =
      extracted.appendWithSession(Seq(publishTo in core := publishToSettings), oldState)

    // create sonatypeReleaseCommand with releaseSonatype step
    val sonatypeCommand = Command.command("sonatypeReleaseCommand") {
      "project core" ::
        "publish" ::
        "sonatypeRelease" ::
        _
    }

    newState.copy(definedCommands = newState.definedCommands :+ sonatypeCommand)
  } else {
    val publishToSettings =
      Some("snapshots".at("https://oss.sonatype.org/" + "content/repositories/snapshots"))
    val newState =
      extracted.appendWithSession(Seq(publishTo in core := publishToSettings), oldState)

    // create sonatypeReleaseCommand without releaseSonatype step
    val sonatypeCommand = Command.command("sonatypeReleaseCommand") {
      "project core" ::
        "publish" ::
        _
    }

    newState.copy(definedCommands = newState.definedCommands :+ sonatypeCommand)
  }
})

lazy val sonatypePublishStage = Seq[ReleaseStep](
  releaseStepCommandAndRemaining(";sonatypeReleaseCommand")
)

lazy val initReleaseStage = Seq[ReleaseStep](
  inquireVersions, // have a developer confirm versions
  setReleaseVersion,
  setSonatypeReleaseSettings
)

lazy val finalReleaseStage = Seq[ReleaseStep](
  releaseStepCommand("project root"), // use version.sbt file from root
  commitReleaseVersion,
  setNextVersion,
  commitNextVersion
)

def getPropertyFlagOrDefault(name: String, value: Boolean): Boolean =
  sys.props.get(name).flatMap(propValue => Try(propValue.toBoolean).toOption).getOrElse(value)

releaseProcess :=
  initReleaseStage ++
    sonatypePublishStage ++
    finalReleaseStage

// Let's do things in parallel!
addCommandAlias(
  "validate",
  "; root/clean; " +
    "all core/headerCheck core/test:headerCheck " +
    "api/headerCheck api/test:headerCheck api/it:headerCheck " +
    "mysql/headerCheck mysql/test:headerCheck mysql/it:headerCheck " +
    "r53/headerCheck r53/test:headerCheck r53/it:headerCheck " +
    "sqs/headerCheck sqs/test:headerCheck sqs/it:headerCheck " +
    "portal/headerCheck portal/test:headerCheck; " +
    "portal/createJsHeaders;portal/checkJsHeaders;" +
    "root/compile;root/test:compile;root/it:compile"
)

addCommandAlias(
  "verify",
  "; project root; coverage; " +
    "all test it:test; " +
    "project root; coverageReport; coverageAggregate"
)

// Build the artifacts for release
addCommandAlias("build-api", ";project api;clean;coverageOff;assembly")
addCommandAlias("build-portal", ";project portal;clean;coverageOff;preparePortal;dist")
addCommandAlias("build", ";build-api;build-portal")
