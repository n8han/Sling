import sbt._

class SlingProject(info: ProjectInfo) extends DefaultWebProject(info)
{
  val databinder_net = "Databinder repository" at "http://databinder.net/repo"
  val lag_net = "lag.net repository" at "http://www.lag.net/repo"

  val js_classpath = outputPath / "js_classes"
  val js_managed = (path("src_managed") / "main" ##) / "js"
  val wmd_src = js_managed / "wmd"
  val showdown_js = wmd_src / "showdown.js"

  override def mainClass = Some("sling.Server")
  override def unmanagedClasspath = super.unmanagedClasspath +++ js_classpath
  
  val jetty = "org.mortbay.jetty" % "jetty-ajp" % "6.1.17"
  val dispatch = "net.databinder" % "dispatch" % "0.3.0"
  val rhino = "rhino" % "js" % "1.7R1"

  override def ivyXML =
    <dependencies>
      <dependency org="slinky" name="slinky" rev="2.1" conf="default">
        <artifact name="slinky" url="http://slinky2.googlecode.com/svn/artifacts/2.1/slinky.jar" />
      </dependency>
      <dependency org="com.workingmouse" name="scalaz" rev="3.3" conf="default">
        <artifact name="scalaz" url="http://scalaz.googlecode.com/svn/artifacts/3.3/scalaz.jar" />
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
  override def extraWebappFiles = js_managed ** ("*")

  lazy val script = task {
    FileUtilities.write((info.projectPath / "run.sh").asFile,
      "java -cp " + 
      (Path.makeString(runClasspath.get) :: scalaJars.map(_.getPath).toList)
        .mkString(java.io.File.pathSeparator) + 
      " $JAVA_OPTIONS " + mainClass.mkString + "\n"
    , log)
  } dependsOn prepareWebapp
}

