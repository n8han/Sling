package sling

import net.lag.configgy.Configgy

object Server {
  def main(args: Array[String]) {
    Configgy.configure("/etc/sling.conf")
    unfiltered.server.Ajp(9000).resources(
        getClass.getResource("/webroot/robots.txt")
    ).filter(new App).run()
  }
}
