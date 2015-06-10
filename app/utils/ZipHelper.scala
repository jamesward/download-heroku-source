package utils

import java.io.File
import java.util.Date
import sbt.{IO, RichFile}

import scala.sys.process.{ProcessLogger, Process}

// lifted from: https://github.com/sbt/sbt-native-packager/blob/master/src/main/scala/com/typesafe/sbt/packager/universal/ZipHelper.scala
object ZipHelper {

  case class FileMapping(file: File, name: String, unixMode: Option[Int] = None)

  /**
   * Creates a zip file attempting to give files the appropriate unix permissions using Java 6 APIs.
   * @param sources   The files to include in the zip file.
   * @param outputZip The location of the output file.
   */
  def zipNative(sources: Traversable[(File, String)], outputZip: File): Unit =
    IO.withTemporaryDirectory { dir =>
      val name = outputZip.getName
      val zipDir = new RichFile(dir) / (if (name endsWith ".zip") name dropRight 4 else name)
      val files = for {
        (file, name) <- sources
      } yield file -> (new RichFile(zipDir) / name)

      IO.copy(files)

      for {
        (src, target) <- files
        // roll back the date 24 hours because the zip stores the files in local time which means they are in the future for some users in some unzip clients
        _ = target.setLastModified(new Date().getTime - (1000 * 60 * 60 * 24))
        if src.canExecute
      } target.setExecutable(true, true)

      val dirFileNames = Option(zipDir.listFiles) getOrElse Array.empty[java.io.File] map (_.getName)

      val logger = ProcessLogger(_ => Unit)

      Process(Seq("zip", "-r", name) ++ dirFileNames, zipDir).!(logger) match {
        case 0 => ()
        case n => sys.error("Failed to run native zip application!")
      }

      IO.copyFile(new RichFile(zipDir) / name, outputZip)
    }
}