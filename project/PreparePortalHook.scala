import scala.sys.process.Process
import play.sbt.PlayRunHook
import sbt.File

object PreparePortalHook {
  def apply(base: File): PlayRunHook = {
    object GruntProcess extends PlayRunHook {
      override def beforeStarted(): Unit = {
        Process("./prepare-portal.sh", base).!
      }
    }
    GruntProcess
  }
}
