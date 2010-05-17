package sling

import org.mortbay.jetty.Connector
import org.mortbay.jetty.{Server => JettyServer}
import org.mortbay.resource.Resource
import org.mortbay.jetty.handler.ResourceHandler
import org.mortbay.jetty.servlet.{ServletHolder, Context}
import org.mortbay.jetty.ajp.Ajp13SocketConnector

import net.lag.configgy.Configgy


object Server {
  def main(args: Array[String]) {
    Configgy.configure("/etc/sling.conf")
    unfiltered.server.Http(8080)(new App)
  }
}
