package sling

import net.lag.configgy.Configgy

object Server {
  def main(args: Array[String]) {
    Configgy.configure("/etc/sling.conf")
    unfiltered.server.Http(8080)(new App)
  }
}
