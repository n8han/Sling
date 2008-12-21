import sbt._

class FridayBuild(info: ProjectInfo) extends DefaultProject(info)
{
  override def mainClass = Some("net.friday.Server")
}
