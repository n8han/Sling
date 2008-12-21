package net.friday

import scala.util.matching.Regex
import Function.curried
import scalaz.OptionW._
import scalaz.EitherW._
import scalaz.StringW._
import scalaz.control.MonadW.{EitherMonad, OptionMonad, EitherLeftMonad, ListMonad}
import slinky.http.servlet.{SlinkyServlet,HttpServlet, HttpServletRequest, ServletApplication, StreamStreamServletApplication}
import slinky.http.servlet.HttpServlet._
import slinky.http.servlet.StreamStreamServletApplication.resourceOr
import slinky.http.{Application, ContentType}
import slinky.http.StreamStreamApplication._
import slinky.http.request.Request.Stream.{MethodPath, Path}
import slinky.http.request.{Request, GET}
import slinky.http.response.{OK, NotFound, BadRequest}
import slinky.http.response.xhtml.Doctype.strict
import slinky.http.response.StreamResponse.{response, statusLine}

import net.databinder.dispatch._

final class App extends StreamStreamServletApplication {
  import App._
  val application =
    (app(_: Request[Stream])) or (req => {
      implicit val r = req
      NotFound << strict << doc("Not Found", "Page not found")
    })
}

object PageDoc extends Doc {
  val body = String('body)
}

import scalaz.CharSet.UTF8

object App {
  implicit val charSet = UTF8
  
  val friday = new Database("friday")
  object IdPath extends Regex("/([^/]+)$") {
    def to_path(id: String) = id.replaceAll(" ", "_")
    def to_id(web: String) = web.replaceAll("_", " ")
    def unapplySeq(str: String) = super.unapplySeq(str).map(_.map(to_id))
  }
    
  def app(implicit request: Request[Stream]) =
    request match {
      case Path("/") => Some(redirect("Home"))

      case Path(IdPath(id)) => try {
        Some(OK(ContentType, "text/html; charset=UTF-8") << 
          strict << doc("Friday — " + id, 
            (friday(id) >> { new Store(_) })(PageDoc.body).mkString("")
          )
        )
      } catch { case e: UnexpectedResponse => None }

      case _ => None
    }

  def doc[A](title: String, a: A) =
    <html xmlns="http://www.w3.org/1999/xhtml">
      <head>
        <title>{ title }</title>
      </head>
      <body>
        { menu }
        { a }
      </body>
    </html>
  
  def menu =
    <ul>
      {
        friday.all_docs map { id => 
          <li> <a href={ IdPath.to_path(id) }> { id } </a> </li> 
        }
      }
    </ul>
}