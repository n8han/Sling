package sling

import org.mortbay.jetty.Connector
import org.mortbay.jetty.{Server => JettyServer}
import org.mortbay.resource.Resource
import org.mortbay.jetty.handler.{ContextHandlerCollection, ResourceHandler}
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

    val robots = getClass.getResource("/webroot/robots.txt").toString
    val webroot = robots.substring(0, robots.lastIndexOf("/"))

    val contexts = new ContextHandlerCollection
    val context = new Context(contexts,"/")
    val holder = new ServletHolder(new slinky.http.servlet.StreamStreamServlet)
    holder.setInitParameter("application", "sling.App")
    context.addServlet(holder, "/*")

    val resource_handler = new ResourceHandler
    val res = Resource.newResource(webroot)
    resource_handler.setBaseResource(res)

    server.addHandler(resource_handler)
    server.addHandler(contexts)

    server.setStopAtShutdown(true)
    server.start()
    server.join()
  }
}
