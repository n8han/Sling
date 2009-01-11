package net.databinder.sling

import org.mortbay.jetty.Connector
import org.mortbay.jetty.{Server => JettyServer}
import org.mortbay.jetty.webapp.WebAppContext
import org.mortbay.jetty.ajp.Ajp13SocketConnector

object Server extends Application {
  val conn = new Ajp13SocketConnector()
  conn.setPort(Integer.getInteger("jetty.ajp.port", 9000).intValue)
  val server = new JettyServer()
  server.addConnector(conn)
  server.addHandler(new WebAppContext(server, "target/webapp", "/"))
  server.setStopAtShutdown(true)
  server.start()
  server.join()
}
