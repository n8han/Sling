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

import dispatch._
import dispatch.couch._
import dispatch.json._

import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils

final class App extends StreamStreamServletApplication {
  import App._
  val application =
    (app(_: Request[Stream])) or (req => {
      implicit val r = req
      NotFound(ContentType, content_type) << strict << Page(new TitledContent {
        val title = "Not Found"
        val body = <p> Page not found </p>
      }).html
    })
}

object PageDoc extends Doc {
  val body: Extract[String] = 'body ? str
}

object App {
  import Js._
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
                  PageDoc.body(Js(entity.getContent())).toList
                )
/*              case (id) if request !? "edit" =>
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
                ))*/
              case (id) =>
                Some(cache_heds(ri, OK(ContentType, content_type)) << strict << 
                  Page(UserDocument(couched, id, showdown.makeHtml(
                    PageDoc.body(Js(entity.getContent()))
                  ).toString)).html
                )
            }
          case (NotModified.toInt, ri, _) => Some(cache_heds(ri, NotModified))
          case (NotFound.toInt, _, _) => None 
        }

      case Path(Index(db)) => Some(redirect(DbId(db, db(couch).all_docs.first)))
      case _ => None
    }

  trait Press { val html: Elem }
  
q  case class Page(content: Content) extends Press {
    val html =
      <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
          <link rel="stylesheet" href="/css/blueprint/screen.css" type="text/css" media="screen, projection" />
          <link rel="stylesheet" href="/css/blueprint/print.css" type="text/css" media="print" /> 
          <link rel="stylesheet" href="style.css" type="text/css" media="screen, projection" /> 
          { content.head }
        </head>
        <body>
          { content.body }
        </body>
      </html>
  }
  trait Content { val head: Elem; val body: Elem }
  
  trait TitledContent extends Content {
    val title: String
    val head = <title> { title } </title>
  }
  
  trait Document extends TitledContent {
    val db: Database#H
    val curr_id: String
    val title = db.name.capitalize + " → " + curr_id
  }

  case class UserDocument(db: Database#H, curr_id: String, text: String) extends Document {
    val body =
      <div id="content">
        <div class="container">
          <h2>{ title }</h2>
          { TOC(db, curr_id) }
          { text }
        </div>
      </div>
  }
  
  case class TOC(db: Database#H, curr_id: String) extends Press {
    val html = <h4><ul>
      {
        db.all_docs map {
          case "style.css" => Nil
          case `curr_id` => <li> { curr_id } </li>
          case id => <li> <a href={ DbId(db, id) }>{ id }</a> </li> 
        }
      }
    </ul></h4>
  }
}
