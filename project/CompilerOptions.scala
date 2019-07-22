import sbt._

object CompilerOptions {

  lazy val scalac_2_12_Options = Seq(
    "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8",                // Specify character encoding used by source files.
    "-explaintypes",                     // Explain type errors in more detail.
    "-feature",                          // Emit warning for usages of features that should be imported explicitly.
    "-language:higherKinds",             // Allow higher-kinded types
    "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
    "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
    "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field
    "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
    "-Xlint:unsound-match",              // Pattern match may not be typesafe.
    "-Xfatal-warnings",                  // Enable failure of compilation when warnings exist.
    "-Ypartial-unification",             // Enable partial unification in type constructor inference
    "-Ywarn-dead-code",                  // Warn when dead code is identified.
    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",              // Warn if a local definition is unused.
    "-Ywarn-unused:params",              // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates"             // Warn if a private member is unused.
  )

  def scalacOptionsByV(scalaVersion: String): Seq[String] = {
    val VersionNumber((major +: minor +: _ +: _), _, _) = scalaVersion
    (major, minor) match {
      case (2, minV) if minV > 11 ⇒ scalac_2_12_Options
      case _ ⇒ sys.error(s"scala version $scalaVersion is not supported.")
    }
  }
}
