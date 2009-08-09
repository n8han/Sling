import sbt._

class SlingProject(info: ProjectInfo) extends DefaultWebProject(info)
{
  val databinder_net = "Databinder repository" at "http://databinder.net/repo"
  val lag_net = "lag.net repository" at "http://www.lag.net/repo"

  val js_classpath = outputPath / "js_classes"
  val js_managed = (path("src_managed") / "main" ##) / "js"
  val wmd_src = js_managed / "wmd"
  val showdown_js = wmd_src / "showdown.js"
  val duel_js = webappPath / "js" / "duel.js"

  override def mainClass = Some("sling.Server")
  override def unmanagedClasspath = super.unmanagedClasspath +++ js_classpath
  
  val jetty_embed = "org.mortbay.jetty" % "jetty-ajp" % "6.1.17"
  val dispatch = "net.databinder" % "dispatch" % "0.3.3"
  val rhino = "rhino" % "js" % "1.7R1"
  val slinky = "slinky" % "slinky" % "2.1" from "http://slinky2.googlecode.com/svn/artifacts/2.1/slinky.jar"
  val scalaz = "com.workingmouse" % "scalaz" % "3.3" from "http://scalaz.googlecode.com/svn/artifacts/3.3/scalaz.jar"
  val configgy = "net.lag" % "configgy" % "1.3"

  lazy val wmd = fileTask(wmd_src :: Nil) {
    import FileUtilities._ 
    val url = new java.net.URL("http://wmd-editor.com/downloads/wmd-1.0.1.zip")
    val wmd_zip = outputPath / "wmd_zip"
    unzip(url, wmd_zip, "wmd-*/wmd/**", log).left.toOption orElse {
      val files = (wmd_zip ** "wmd" ##) ** "*"
      copy(files.get, wmd_src, log).left.toOption
    }
  }

  lazy val duel = fileTask(js_classpath from List(duel_js, showdown_js)) {
    import FileUtilities._ 
    val duel_together = outputPath / "Duel.js"
    copyFile(duel_js, duel_together, log) orElse
    readStream(showdown_js.asFile, log) { in =>
      appendStream(duel_together.asFile, log) { out =>
        transfer(in, out, log)
      }
    } orElse
    FileUtilities.clean(js_classpath :: Nil, true, log) orElse
    createDirectory(js_classpath, log) orElse 
      Run.run(
        "org.mozilla.javascript.tools.jsc.Main",
        descendents(managedDependencyPath, "js-*.jar").get,
        "-d" :: js_classpath.toString :: "-package" :: "js" :: "-extends" :: "java.lang.Object" ::
          duel_together.asFile.toString :: Nil,
        log
      )
  } dependsOn wmd
  
  override def compileAction = super.compileAction dependsOn(duel)
  override def extraWebappFiles = js_managed ** ("*")

  lazy val script = task {
    FileUtilities.write((info.projectPath / "run.sh").asFile,
      "java -cp " + 
      (Path.makeString(runClasspath.get) :: mainDependencies.scalaJars.get.map(_.asFile).toList)
        .mkString(java.io.File.pathSeparator) + 
      " $JAVA_OPTIONS " + mainClass.mkString + "\n"
    , log)
  } dependsOn prepareWebapp
}

