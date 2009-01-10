package net.databinder.sling

import slinky.http.servlet.StreamStreamServletApplication
import slinky.http.servlet.StreamStreamServletApplication.resourceOr
import slinky.http.{ContentType, Date, CacheControl}
import slinky.http.StreamStreamApplication._
import slinky.http.request.Request.Stream.{MethodPath, Path}
import slinky.http.request.{Request, GET, IfNoneMatch, RequestHeader}
import slinky.http.response.{Response, OK, NotFound, ETag, NotModified}
import slinky.http.response.xhtml.Doctype.strict
import scalaz.OptionW.onull
import scalaz.CharSet.UTF8

import net.databinder.dispatch._
import org.apache.http.HttpResponse

final class App extends StreamStreamServletApplication {
  import App._
  val application =
    (app(_: Request[Stream])) or (req => {
      implicit val r = req
      NotFound(ContentType, content_type) << strict << doc(None, "Not Found", "Page not found")
    })
}

object PageDoc extends Doc {
  val body = String('body)
}

object App {
  implicit val charSet = UTF8
  def content_type = "text/html; charset=UTF-8"
  
  object DbId {
    def to_path(id: String) = id.replaceAll(" ", "_")
    def to_id(web: String) = web.replaceAll("_", " ")
    val Re = "^/([a-z_]+)(?:/([a-z]+))?/([^/]+)$".r
    def unapply(path: String) = path match {
      case Re(db, act, id) => Some(Database(db), onull(act), to_id(id))
      case _ => None
    }
    def apply(db: Database, act: Option[String], id: String) = 
      "/" + (db.name :: act.toList ::: id :: Nil).mkString("/")
  }
  object Index {
    val Re =  "^/([a-z_]+)/?$".r
    def unapply(path: String) = path match { 
      case Re(db) => Some(Database(db)) 
      case _ => None
    }
  }
  
  val showdown = new js.Showdown()
  def md2html(md: String) = scala.xml.Unparsed(showdown.makeHtml(md).toString)
  
  def couch = Couch()
  
  def cache_heds(ri: HttpResponse, ro: Response[Stream]) = 
    ro(Date, ri.getFirstHeader(Date).getValue)(CacheControl, "max-age=600")(ETag, ri.getFirstHeader(ETag).getValue)
  
  def app(implicit request: Request[Stream]) =
    request match {
      case Path(DbId(db, act, id)) =>
        val couched = db( (couch /: request.headers) {
          case (c, (k, v)) if k.asString == IfNoneMatch.asString =>
            c << (k.asString, v.mkString.replace("-gzip","")) // gross: https://issues.apache.org/bugzilla/show_bug.cgi?id=39727
          case (c, _) => c
        })
        
        couched(id) {
          case (OK.toInt, ri, Some(entity)) =>
            id match {
              case "style.css" =>
                Some(cache_heds(ri, OK(ContentType, "text/css; charset=UTF-8")) << 
                  new Store(entity.getContent())(PageDoc.body).mkString("").toList
                )
              case id =>
                Some(cache_heds(ri, OK(ContentType, content_type)) << strict << doc(
                  Some(couched), id, md2html(new Store(entity.getContent())(PageDoc.body).mkString(""))
                ))
            }
          case (NotModified.toInt, ri, _) => Some(cache_heds(ri, NotModified))
          case (NotFound.toInt, _, _) => None 
        }

      case Path(Index(db)) => Some(redirect(DbId(db, None, db(couch).all_docs.first)))
      case _ => None
    }

  def doc[A](db: Option[Database#H], curr_id: String, body: A) = {
    val title = db.map(d => d.name.capitalize + " → ").mkString + curr_id
    <html xmlns="http://www.w3.org/1999/xhtml">
      <head>
        <title>{ title }</title>
        <link rel="stylesheet" href="/blueprint/screen.css" type="text/css" media="screen, projection" />
        <link rel="stylesheet" href="/blueprint/print.css" type="text/css" media="print" /> 
        <link rel="stylesheet" href="style.css" type="text/css" media="screen, projection" /> 
      </head>
      <body>
        <div class="container">
          <h2>{ title }</h2>
          <h4>{ menu(db, curr_id) }</h4>
          { body }
        </div>
      </body>
    </html>
  }
  
  def menu(db: Option[Database#H], curr_id: String) =
    <ul>
      {
        db map { _.all_docs map {
          case "style.css" => ""
          case `curr_id` => <li> { curr_id } </li>
          case id => <li> <a href={ DbId.to_path(id) }>{ id }</a> </li> 
        } } getOrElse ""
      }
    </ul>
}
