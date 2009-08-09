package sling

import org.mortbay.jetty.Connector
import org.mortbay.jetty.{Server => JettyServer}
import org.mortbay.jetty.handler.ContextHandlerCollection
import org.mortbay.jetty.servlet.{ServletHolder, Context}
import org.mortbay.jetty.ajp.Ajp13SocketConnector

import net.lag.configgy.Configgy

object Server {
  def main(args: Array[String]) {
    App // App initializes Configgy
    val conn = new Ajp13SocketConnector()
    conn.setPort(Configgy.config.getInt("jetty.ajp.port", 9000))
    val server = new JettyServer()
    server.addConnector(conn)
    val contexts = new ContextHandlerCollection
    server.setHandler(contexts)

    val context = new Context(contexts,"/")
    val holder = new ServletHolder(new slinky.http.servlet.StreamStreamServlet)
    holder.setInitParameter("application", "sling.App")
    context.addServlet(holder, "/*");

    server.setStopAtShutdown(true)
    server.start()
    server.join()
  }
}
