import sbt._

class SlingProject(info: ProjectInfo) extends DefaultProject(info) with AssemblyProject
{
  val lag_net = "lag.net repository" at "http://www.lag.net/repo"
  val databinder_net = "databinder.net repository" at "http://databinder.net/repo"

  val js_classpath = outputPath / "js_classes"
  val js_managed = (path("src_managed") / "main" ##) / "js"
  val wmd_src = js_managed / "wmd"
  val showdown_js = wmd_src / "showdown.js"
  val webroot = outputPath / "resources" / "webroot"
  val duel_js = webroot / "js" / "duel.js"

  override def mainClass = Some("sling.Server")
  override def unmanagedClasspath = super.unmanagedClasspath +++ js_classpath
  
  val dispatch_version = "0.7.3"
  val dispatch_couch = "net.databinder" %% "dispatch-couch" % dispatch_version
  val dispatch_twitter = "net.databinder" %% "dispatch-twitter" % dispatch_version
  val unfiltered = "net.databinder" %% "unfiltered-ajp-server" % "0.1.3-SNAPSHOT"
  val rhino = "rhino" % "js" % "1.7R1"
  val configgy = "net.lag" % "configgy" % "1.3" intransitive()

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
  
  override def compileAction = super.compileAction.dependsOn(duel, copyResources)
  override def copyResourcesAction = super.copyResourcesAction && copyJsManaged
  
  lazy val copyJsManaged = copyTask(js_managed ** "*", webroot)
}

