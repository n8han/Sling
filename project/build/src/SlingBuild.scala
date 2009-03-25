import sbt._

class SlingBuild(info: ProjectInfo) extends DefaultWebProject(info)
{
  val lag_net = "lag.net repository" at "http://www.lag.net/repo"

  val js_classpath = outputPath / "js_classes"
  val js_src = (path("src_managed") / "main" ##) / "js"
  val wmd_src = js_src / "wmd"
  val showdown_js = wmd_src / "showdown.js"

  override def mainClass = Some("sling.Server")
  override def unmanagedClasspath = super.unmanagedClasspath +++ js_classpath
  
  val jetty = "org.mortbay.jetty" % "jetty-ajp" % "6.1.14"
  val dispatch = "net.databinder" % "dispatch" % "0.1-SNAPSHOT"
  val rhino = "rhino" % "js" % "1.7R1"
  val scalaz = "com.workingmouse" % "scalaz" % "3.0"

  override def ivyXML =
    <dependencies>
      <dependency org="slinky" name="slinky" rev="2.1" conf="default">
        <artifact name="slinky" url="http://slinky2.googlecode.com/svn/artifacts/2.1/slinky.jar" />
      </dependency>
    </dependencies>

  
  lazy val wmd = fileTask(wmd_src :: Nil) {
    import FileUtilities._ 
    val toAppend = "\nfunction makeHtml(md) { return new Showdown.converter().makeHtml('' + md) }" 
    val url = new java.net.URL("http://wmd-editor.com/downloads/wmd-1.0.1.zip")
    val wmd_zip = outputPath / "wmd_zip"
    unzip(url, wmd_zip, "wmd-*/wmd/**", log).left.toOption orElse {
      val files = (wmd_zip ** "wmd" ##) ** "*"
      copy(files.get, wmd_src, log).left.toOption
    } orElse append(showdown_js.asFile, toAppend, log)
  }

  lazy val showdown = fileTask(js_classpath from showdown_js) {
    FileUtilities.createDirectory(js_classpath, log) orElse {
      Run.run(
        "org.mozilla.javascript.tools.jsc.Main",
        descendents(managedDependencyPath, "js-*.jar").get,
        "-d" :: js_classpath.toString :: "-package" :: "js" :: "-extends" :: "java.lang.Object" ::
          showdown_js.asFile.toString :: Nil,
        log
      )
    }
  } dependsOn wmd
  
  override def compileAction = super.compileAction dependsOn(showdown)
  override def extraWebappFiles = js_src ** ("*")

  lazy val script = task {
    FileUtilities.write((info.projectPath / "run.sh").asFile,
      "java -cp " + 
      (Path.makeString(runClasspath.get) :: scalaJars.map(_.getPath).toList)
        .mkString(java.io.File.pathSeparator) + 
      " $JAVA_OPTIONS " + mainClass.mkString + "\n"
    , log)
  } dependsOn prepareWebapp
}
