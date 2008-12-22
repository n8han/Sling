import sbt._
import java.util.zip.ZipInputStream
import java.io.FileOutputStream
import java.net.URL

class FridayBuild(info: ProjectInfo) extends DefaultProject(info)
{
  val js_sources = outputPath / "js"
  val js_classpath = outputPath / "js_classes"

  override def mainClass = Some("net.friday.Server")
  override def compileClasspath = super.compileClasspath +++ (js_classpath ##)
  override def runClasspath = super.runClasspath +++ (js_classpath ##)
  
  lazy val showdown = task {
    FileUtilities.createDirectories(js_sources.asFile :: js_classpath.asFile :: Nil, log)
    val showdown_js = js_sources / "Showdown.js"
    def unzip(zis: ZipInputStream) {
      if (zis.getNextEntry.getName == "src/showdown.js") {
        def write(from: ZipInputStream, to: FileOutputStream) {
          from.read() match {
            case -1 => 
              from.close()
              to.write("\nfunction makeHtml(md) { return new Showdown.converter().makeHtml('' + md) }".getBytes)
              to.close()
            case c => to.write(c); write(from, to)
          }
        }
        write(zis, new FileOutputStream(showdown_js.asFile))
      }
      else unzip(zis)
    }
    unzip(new ZipInputStream(new URL("http://attacklab.net/showdown/showdown-v0.9.zip").openStream()))
    Run(
      Some("org.mozilla.javascript.tools.jsc.Main"), 
      descendents(managedDependencyPath, "js-*.jar").get,
      "-d" :: js_classpath.toString :: "-package" :: "js" :: "-extends" :: "java.lang.Object" :: showdown_js.toString :: Nil,
      log
    )
    None
  }
}
