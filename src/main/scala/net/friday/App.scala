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
      NotFound << strict << doc("Not Found", "Page not found")
    })
}

object PageDoc extends Doc {
  val body = String('body)
}

import scalaz.CharSet.UTF8

object App {
  implicit val charSet = UTF8
  
  object IdPath extends Regex("/([^/]+)$") {
    def to_path(id: String) = id.replaceAll(" ", "_")
    def to_id(web: String) = web.replaceAll("_", " ")
    def unapplySeq(str: String) = super.unapplySeq(str).map(_.map(to_id))
  }
  
  val showdown = new js.Showdown()
  def md2html(md: String) = scala.xml.Unparsed(showdown.makeHtml(md).toString)
  
  def app(implicit request: Request[Stream]) =
    request match {
      case Path("/") => Some(redirect("Home"))

      case Path(IdPath(id)) => try {
        val friday = new Database("friday") {
          request.headers.foreach {
            case (k, v) if k.asString == IfNoneMatch.asString =>
              preflight(_.addHeader(IfNoneMatch.asString, v.mkString))
            case (k,v) =>
          }
        }
        
        friday.g("/friday/"+id) { (code, res, entity) =>
          code match {
            case 200 =>
              val etag = res.getFirstHeader(ETag).getValue
              Some(OK(ContentType, "text/html; charset=UTF-8")(ETag, etag) << 
                strict << doc("Friday — " + id, 
                  md2html(new Store(entity.getContent())(PageDoc.body).mkString(""))
                )
              )
            case 304 => Some(response(NotModified))
            case _ => None
          }
        }
      } catch { case e: UnexpectedResponse => None }

      case _ => None
    }

  def doc[A](title: String, body: A) =
    <html xmlns="http://www.w3.org/1999/xhtml">
      <head>
        <title>{ title }</title>
        <link rel="stylesheet" href="blueprint/screen.css" type="text/css" media="screen, projection" />
        <link rel="stylesheet" href="blueprint/print.css" type="text/css" media="print" /> 
        <link rel="stylesheet" href="friday.css" type="text/css" media="screen, projection" /> 
      </head>
      <body>
        <div class="container">
          <h2>{ title }</h2>
          <h4>{ menu }</h4>
          { body }
        </div>
      </body>
    </html>
  
  def menu =
    <ul>
      {
        new Database("friday").all_docs map { id => 
          <li> <a href={ IdPath.to_path(id) }> { id } </a> </li> 
        }
      }
    </ul>
}
