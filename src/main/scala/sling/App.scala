package sling

import slinky.http.servlet.StreamStreamServletApplication
import slinky.http.servlet.StreamStreamServletApplication.resourceOr
import slinky.http.{ContentType, Date, CacheControl}
import slinky.http.StreamStreamApplication._
import slinky.http.request.Request.Stream.{MethodPath, Path}
import slinky.http.request.{Request, GET, RequestHeader}
import slinky.http.response.{Response, OK, NotFound, ETag, NotModified}
import slinky.http.response.xhtml.Doctype.strict
import scalaz.CharSet.UTF8

import dispatch._
import dispatch.couch._
import dispatch.json._
import dispatch.twitter.Search

import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils

import net.lag.configgy.Configgy

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

object MyCouch {
  def apply() = Couch(
    Configgy.config.getString("couch.host","127.0.0.1"),
    Configgy.config.getInt("couch.port",5984)
  )
}

object PageDoc extends Id {
  val body = 'body ? str
  val tweed = 'tweed ? str
}

object DbId {
  def to_path(id: String) = id.replaceAll(" ", "_")
  def to_id(web: String) = web.replaceAll("_", " ")
  val Re = "^/([a-z_]+)/([^/]+)$".r
  def unapply(path: String) = path match {
    case Re(db, id) => Some(new Db(MyCouch(), db), to_id(id))
    case _ => None
  }
  def apply(db: Db, id: String) = 
    "/" + (db.name :: to_path(id) :: Nil).mkString("/")
}
object Index {
  val Re =  "^/([a-z_]+)/?$".r
  def unapply(path: String) = path match { 
    case Re(db) => Some(Db(MyCouch(), db)) 
    case _ => None
  }
}
// extract etag between quotes and discard any gzip addition
// (https://issues.apache.org/bugzilla/show_bug.cgi?id=39727)
object ET extends util.matching.Regex("\"(.*)\"(?:-gzip)?") {
  def apply(tag: String) = '"' + tag + '"'
}
object NumTag {
  val Num = "(\\d+)".r
  def unapply(tag: String) = tag match {
    case Num(str) => Some(BigDecimal(str))
    case _ => None
  }
  def apply(num: Number) = num.toString
}
object SpliceTag {
  def unapplySeq(tag: String) = Some(tag.split('|'))
  def apply(seq: String*) =  seq.mkString("|")
}

object App {
  Configgy.configure("/etc/sling.conf")
  import Js._
  implicit val charSet = UTF8
  def content_type = "text/html; charset=UTF-8"
  
  def cache_heds(ri: HttpResponse, ro: Response[Stream], combo_tag: String) = 
    ro(Date, ri.getFirstHeader(Date).getValue)(CacheControl, "max-age=600")(ETag, combo_tag)
  
  def app(implicit request: Request[Stream]) = {
    val http = new Http
    request match {
      case Path(DbId(db, id)) =>
        val IfNoneMatch = "If-None-Match"
        val (couch_et, tweed, tweed_js) =
          request.headers.find {
            case (k, v) => k.asString == IfNoneMatch
          } map { _._2.mkString } map {
            case ET(NumTag(couch_et)) =>
              (Some(couch_et), None, None)
            case ET(SpliceTag(NumTag(couch_et), tweed, NumTag(latest))) =>
              val res = http(Search(tweed).results)
              res.firstOption.filter { case Search.id(id) => id == latest } map { js =>
                (Some(couch_et), Some(tweed), Some(res))
              } getOrElse { (None, Some(tweed), Some(res)) }
            case _ => (None, None, None)
          } getOrElse (None, None, None)
        val with_heds = couch_et map { tag => /\ <:< Map(IfNoneMatch -> ET(NumTag(tag))) } getOrElse /\
        (http x with_heds <& Doc(db, id)) {
          case (OK.toInt, ri, Some(entity)) =>
            val ET(NumTag(couch_et)) = ri.getFirstHeader(ETag).getValue
            id match {
              case ("style.css") =>
                Some(cache_heds(ri, OK(ContentType, "text/css; charset=UTF-8"), ET(NumTag(couch_et))) << 
                  PageDoc.body(Js(entity.getContent())).toList
                )
              case (id) if request !? "edit" =>
                Some(cache_heds(ri, OK(ContentType, content_type), ET(NumTag(couch_et))) << strict << 
                  Page(EditDocument(TOC(db, http, id, "?edit"), 
                    EntityUtils.toString(entity, UTF8)
                  )).html
                )
              case (id) =>
                val js = Js(entity.getContent())
                val PageDoc.body(md) = js
                val (combo_tag, tweedy) = js match {
                  case PageDoc.tweed(t) => 
                    val ljs = tweed_js.getOrElse { http(Search(t).results) }
                    val ct: String = ljs.firstOption.map {
                      case Search.id(id) => ET(SpliceTag(NumTag(couch_et), t, NumTag(id)))
                    } getOrElse ET(NumTag(couch_et))
                    (ct, Some(t, ljs))
                  case _ => ( ET(NumTag(couch_et)), None )
                }
                Some(cache_heds(ri, OK(ContentType, content_type), combo_tag) << strict << 
                  Page(ShowDocument(TOC(db, http, id, ""), md, tweedy)).html
                )
            }
          case (NotModified.toInt, ri, _) => Some(cache_heds(ri, NotModified, 
            (couch_et, tweed, tweed_js) match {
              case (Some(couch_et), Some(tweed), Some(Search.id(latest) :: _)) => 
                ET(SpliceTag(NumTag(couch_et), tweed, NumTag(latest)))
              case (Some(couch_et), _, _) => ET(NumTag(couch_et))
              case _ => ""
            }
          ) )
          case (NotFound.toInt, _, _) => None 
        }

      case Path(Index(db)) => Some(redirect(DbId(db, http(db.all_docs).first)))
      case _ => None
    }
  }
}
