package net.friday

import org.mortbay.jetty.Connector
import org.mortbay.jetty.{Server => JettyServer}
import org.mortbay.jetty.webapp.WebAppContext
import org.mortbay.jetty.ajp.Ajp13SocketConnector

object Server extends Application {
  def context(server: JettyServer) = new WebAppContext(server, "src/main/webapp", "/")

  System.getProperty("jetty.ajp.port") match {
    case null =>
      val server = new JettyServer(8080)
      server.addHandler(context(server))
      println(">>> STARTING EMBEDDED JETTY SERVER, PRESS ANY KEY TO STOP")
      server.start()
      while (System.in.available() == 0) {
        Thread.sleep(1000)
      }
      server.stop()
      server.join()
    case p =>
      val conn = new Ajp13SocketConnector()
      conn.setPort(p.toInt)
      val server = new JettyServer()
      server.addConnector(conn)
      server.addHandler(context(server))
      server.setStopAtShutdown(true)
      server.start()
      server.join()
  }
}
