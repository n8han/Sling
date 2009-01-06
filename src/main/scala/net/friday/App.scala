package net.friday

import slinky.http.servlet.StreamStreamServletApplication
import slinky.http.servlet.StreamStreamServletApplication.resourceOr
import slinky.http.{ContentType}
import slinky.http.StreamStreamApplication._
import slinky.http.request.Request.Stream.{MethodPath, Path}
import slinky.http.request.{Request, GET, IfNoneMatch, RequestHeader}
import slinky.http.response.{OK, NotFound, ETag, NotModified}
import slinky.http.response.xhtml.Doctype.strict

import scala.util.matching.Regex
import net.databinder.dispatch._

final class App extends StreamStreamServletApplication {
  import App._
  val application =
    (app(_: Request[Stream])) or (req => {
      implicit val r = req
      NotFound(ContentType, content_type) << strict << doc("Not Found", "Page not found")
    })
}

object PageDoc extends Doc {
  val body = String('body)
}

import scalaz.CharSet.UTF8

object App {
  implicit val charSet = UTF8
  def content_type = "text/html; charset=UTF-8"
  
  object IdPath extends Regex("/([^/]+)$") {
    def to_path(id: String) = id.replaceAll(" ", "_")
    def to_id(web: String) = web.replaceAll("_", " ")
    def unapplySeq(str: String) = super.unapplySeq(str).map(_.map(to_id))
  }
  
  val showdown = new js.Showdown()
  def md2html(md: String) = scala.xml.Unparsed(showdown.makeHtml(md).toString)
  
  def couch = Couch()
  val friday = Database("friday")
  def all_docs = friday.all_docs(couch)
  
  def app(implicit request: Request[Stream]) =
    request match {
      case Path("/") => Some(redirect(IdPath.to_path(all_docs.first)))

      case Path(IdPath(id)) =>
        val c = (couch /: request.headers) {
          case (couch, (k, v)) if k.asString == IfNoneMatch.asString =>
            couch << (k.asString, v.mkString)
          case (couch, _) => couch
        }
        
        friday.doc(c)(id) {
          case (OK.toInt, res, Some(entity)) =>
            Some(OK(ContentType, content_type)(ETag, res.getFirstHeader(ETag).getValue) << 
              strict << doc(id, 
                md2html(new Store(entity.getContent())(PageDoc.body).mkString(""))
              )
            )
          case (NotModified.toInt, _, _) => Some(response(NotModified))
          case (NotFound.toInt, _, _) => None 
        }

      case _ => None
    }

  def doc[A](curr_id: String, body: A) =
    <html xmlns="http://www.w3.org/1999/xhtml">
      <head>
        <title>Friday — { curr_id }</title>
        <link rel="stylesheet" href="blueprint/screen.css" type="text/css" media="screen, projection" />
        <link rel="stylesheet" href="blueprint/print.css" type="text/css" media="print" /> 
        <link rel="stylesheet" href="friday.css" type="text/css" media="screen, projection" /> 
      </head>
      <body>
        <div class="container">
          <h2>Friday — { curr_id }</h2>
          <h4>{ menu(curr_id) }</h4>
          { body }
        </div>
      </body>
    </html>
  
  def menu(curr_id: String) =
    <ul>
      {
        all_docs map {
          case `curr_id` => <li> { curr_id } </li>
          case id => <li> <a href={ IdPath.to_path(id) }>{ id }</a> </li> 
        }
      }
    </ul>
}
