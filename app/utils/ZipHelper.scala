package utils

import java.io.File
import java.net.URL
import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermission
import java.util.Date

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import sbt.{IO, RichFile}

import scala.collection.JavaConverters._
import scala.sys.process.{Process, ProcessLogger}

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

  def downloadAndExtractTarGz(source: String, destination: Path): Unit = {
    // download the tar.gz and transform it to a zip
    val url = new URL(source)
    val inputStream = url.openConnection().getInputStream
    val gzipCompressorInputStream = new GzipCompressorInputStream(inputStream)
    val archiveInputStream = new TarArchiveInputStream(gzipCompressorInputStream)

    Stream
      .continually(archiveInputStream.getNextTarEntry)
      .takeWhile(_ != null)
      .foreach { ze =>
        val tmpFile = new File(destination.toFile, ze.getName)
        if (ze.isDirectory) {
          tmpFile.mkdirs()
        }
        else if (ze.isFile) {
          Files.copy(archiveInputStream, tmpFile.toPath)
          Files.setPosixFilePermissions(tmpFile.toPath, unixModeToPermSet(BigInt(ze.getMode)).asJava)
          tmpFile.setLastModified(ze.getLastModifiedDate.getTime)
        }
      }

    archiveInputStream.close()
    gzipCompressorInputStream.close()
    inputStream.close()
  }

  def unixModeToPermSet(mode: BigInt): Set[PosixFilePermission] = {
    Map(
      PosixFilePermission.OWNER_READ -> BigInt("0400", 8),
      PosixFilePermission.OWNER_WRITE -> BigInt("0200", 8),
      PosixFilePermission.OWNER_EXECUTE -> BigInt("0100", 8),
      PosixFilePermission.GROUP_READ -> BigInt("0040", 8),
      PosixFilePermission.GROUP_WRITE -> BigInt("0020", 8),
      PosixFilePermission.GROUP_EXECUTE -> BigInt("0010", 8),
      PosixFilePermission.OTHERS_READ -> BigInt("0004", 8),
      PosixFilePermission.OTHERS_WRITE -> BigInt("0002", 8),
      PosixFilePermission.OTHERS_EXECUTE -> BigInt("0001", 8)
    ).filter { case (perm, mask) =>
      (mode | mask) == mode
    }.keySet
  }

}