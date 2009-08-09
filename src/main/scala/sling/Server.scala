package sling

import org.mortbay.jetty.Connector
import org.mortbay.jetty.{Server => JettyServer}
import org.mortbay.jetty.webapp.WebAppContext
import org.mortbay.jetty.ajp.Ajp13SocketConnector

import net.lag.configgy.Configgy

object Server {
  def main(args: Array[String]) {
    App // App initializes Configgy
    val conn = new Ajp13SocketConnector()
    conn.setPort(Configgy.config.getInt("jetty.ajp.port", 9000))
    val server = new JettyServer()
    server.addConnector(conn)
    server.addHandler(new WebAppContext(server, "target/webapp", "/"))
    server.setStopAtShutdown(true)
    server.start()
    server.join()
  }
}
