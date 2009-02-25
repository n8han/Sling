package net.databinder.sling

import scala.xml.{Unparsed, Elem}

import slinky.http.servlet.StreamStreamServletApplication
import slinky.http.servlet.StreamStreamServletApplication.resourceOr
import slinky.http.{ContentType, Date, CacheControl}
import slinky.http.StreamStreamApplication._
import slinky.http.request.Request.Stream.{MethodPath, Path}
import slinky.http.request.{Request, GET, IfNoneMatch, RequestHeader}
import slinky.http.response.{Response, OK, NotFound, ETag, NotModified}
import slinky.http.response.xhtml.Doctype.strict
import scalaz.CharSet.UTF8

import net.databinder.dispatch._
import net.databinder.dispatch.couch._
import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils

final class App extends StreamStreamServletApplication {
  import App._
  val application =
    (app(_: Request[Stream])) or (req => {
      implicit val r = req
      NotFound(ContentType, content_type) << strict << doc(None, "Not Found", "Page not found")
    })
}

case class PageDoc(js: Js) extends Doc {
  val body = 'body as str
}

object App {
  implicit val charSet = UTF8
  def content_type = "text/html; charset=UTF-8"
  
  object DbId {
    def to_path(id: String) = id.replaceAll(" ", "_")
    def to_id(web: String) = web.replaceAll("_", " ")
    val Re = "^/([a-z_]+)/([^/]+)$".r
    def unapply(path: String) = path match {
      case Re(db, id) => Some(Database(db), to_id(id))
      case _ => None
    }
    def apply(db: Database, id: String) = 
      "/" + (db.name :: id :: Nil).mkString("/")
  }
  object Index {
    val Re =  "^/([a-z_]+)/?$".r
    def unapply(path: String) = path match { 
      case Re(db) => Some(Database(db)) 
      case _ => None
    }
  }
  
  val showdown = new js.showdown()
  def md2html(md: String) = Unparsed(showdown.makeHtml(md).toString)
  
  def couch = Couch(System.getProperty("couch.host","127.0.0.1"))
  
  def cache_heds(ri: HttpResponse, ro: Response[Stream]) = 
    ro(Date, ri.getFirstHeader(Date).getValue)(CacheControl, "max-age=600")(ETag, ri.getFirstHeader(ETag).getValue)
  
  def app(implicit request: Request[Stream]) =
    request match {
      case Path(DbId(db, id)) =>
        val couched = db( (couch /: request.headers) {
          case (c, (k, v)) if k.asString == IfNoneMatch.asString =>
            c << (k.asString, v.mkString.replace("-gzip","")) // gross: https://issues.apache.org/bugzilla/show_bug.cgi?id=39727
          case (c, _) => c
        })
        
        couched(id) {
          case (OK.toInt, ri, Some(entity)) =>
            id match {
              case ("style.css") =>
                Some(cache_heds(ri, OK(ContentType, "text/css; charset=UTF-8")) << 
                  PageDoc(Js(entity.getContent())).body.toList
                )
              case (id) if request !? "edit" =>
                Some(cache_heds(ri, OK(ContentType, content_type)) << strict << doc(
                  Some(couched), id,
                    <link rel="stylesheet" href="/css/edit.css" type="text/css" media="screen" /> 
                    <script type="text/javascript" src="/script/jquery.js"></script>
                    <script type="text/javascript" src="/script/json2.js"></script>
                    <script type="text/javascript" src="/js/wmd/showdown.js"></script>
                    <script type="text/javascript" src="/js/edit.js"></script>
                    <script type="text/javascript"> 
                      { Unparsed(
                          "var doc = " + EntityUtils.toString(entity, UTF8) + ";" +
                          "var doc_url = '/couch" + DbId(db, id) + "';"
                      ) }
                    </script>
                  ,
                    <div id="edit"><div id="fixed"><div>
                      <div class="container">
                        <form id="form">
                          <div><textarea id="body" name="body"></textarea></div>
                          <div><input type="submit" value="Save Changes" /></div>
                        </form>
                      </div>
                    </div></div></div>
                    <img id="shade" title="Toggle Editor" src="/css/ship-up.gif" />
                  ,
                    <div id="body-preview"></div>
                  , "?edit"
                ))
              case (id) =>
                Some(cache_heds(ri, OK(ContentType, content_type)) << strict << doc(
                  Some(couched), id, md2html(PageDoc(Js(entity.getContent())).body)
                ))
            }
          case (NotModified.toInt, ri, _) => Some(cache_heds(ri, NotModified))
          case (NotFound.toInt, _, _) => None 
        }

      case Path(Index(db)) => Some(redirect(DbId(db, db(couch).all_docs.first)))
      case _ => None
    }

  def doc[A, B](db: Option[Database#H], curr_id: String, body: B): Elem = doc(db, curr_id, Nil, Nil, body, "")
  def doc[A, B, C](db: Option[Database#H], curr_id: String, head: A, top: B, body: C, query: String) = {
    val title = db.map(d => d.name.capitalize + " → ").mkString + curr_id
    <html xmlns="http://www.w3.org/1999/xhtml">
      <head>
        <title>{ title }</title>
        <link rel="stylesheet" href="/css/blueprint/screen.css" type="text/css" media="screen, projection" />
        <link rel="stylesheet" href="/css/blueprint/print.css" type="text/css" media="print" /> 
        <link rel="stylesheet" href="style.css" type="text/css" media="screen, projection" /> 
        { head }
      </head>
      <body>
        { top }
        <div id="content">
          <div class="container">
            <h2>{ title }</h2>
            <h4><ul>
              {
                db map { d => d.all_docs map {
                  case "style.css" => Nil
                  case `curr_id` => <li> { curr_id } </li>
                  case id => <li> <a href={ DbId(d, id) + query }>{ id }</a> </li> 
                } } getOrElse Nil
              }
            </ul></h4>
            { body }
          </div>
        </div>
      </body>
    </html>
  }
}
