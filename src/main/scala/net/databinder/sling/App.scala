package net.databinder.sling

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

object PageDoc extends Doc {
  val body = 'body ? str
  val tweed = 'tweed ? str
}

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
// extract etag between quotes and discard any gzip addition
// (https://issues.apache.org/bugzilla/show_bug.cgi?id=39727)
object ET extends util.matching.Regex("\"(.*)\"(?:-gzip)?") {
  def apply(tag: String) = '"' + tag + '"'
}
object DocTag {
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
  
  def couch = Couch(Configgy.config.getString("couch.host","127.0.0.1"))
  
  def cache_heds(ri: HttpResponse, ro: Response[Stream], combo_tag: String) = 
    ro(Date, ri.getFirstHeader(Date).getValue)(CacheControl, "max-age=600")(ETag, combo_tag)
  
  def app(implicit request: Request[Stream]) =
    request match {
      case Path(DbId(db, id)) =>
        val IfNoneMatch = "If-None-Match"
        val (couch_et, tweed, tweed_js) =
          request.headers.find {
            case (k, v) => k.asString == IfNoneMatch
          } map { _._2.mkString } map {
            case ET(DocTag(couch_et)) =>
              (Some(couch_et), None, None)
            case ET(SpliceTag(DocTag(couch_et), tweed, latest)) =>
              val res = (new Search)(tweed)
              res.firstOption.filter { case Search.id(id) => id.toString == latest } map { js =>
                (Some(couch_et), Some(tweed), Some(res))
              } getOrElse { (None, Some(tweed), Some(res)) }
            case _ => (None, None, None)
          } getOrElse (None, None, None)
        val couched = db(couch_et map { tag => println(ET(DocTag(tag))); couch << (IfNoneMatch, ET(DocTag(tag))) } getOrElse couch)
        couched(id) {
          case (OK.toInt, ri, Some(entity)) =>
            val ET(DocTag(couch_et)) = ri.getFirstHeader(ETag).getValue
            id match {
              case ("style.css") =>
                Some(cache_heds(ri, OK(ContentType, "text/css; charset=UTF-8"), ET(DocTag(couch_et))) << 
                  PageDoc.body(Js(entity.getContent())).toList
                )
              case (id) if request !? "edit" =>
                Some(cache_heds(ri, OK(ContentType, content_type), ET(DocTag(couch_et))) << strict << 
                  Page(EditDocument(TOC(couched, id, "?edit"), 
                    EntityUtils.toString(entity, UTF8)
                  )).html
                )
              case (id) =>
                val js = Js(entity.getContent())
                val PageDoc.body(md) = js
                val (combo_tag, tweedy) = js match {
                  case PageDoc.tweed(t) => 
                    val ljs = tweed_js.getOrElse { (new Search)(t) }
                    val ct: String = ljs.firstOption.map {
                      case Search.id(id) => ET(SpliceTag(DocTag(couch_et), t, id.toString))
                    } getOrElse ET(DocTag(couch_et))
                    (ct, Some(t, ljs))
                  case _ => ( ET(DocTag(couch_et)), None )
                }
                Some(cache_heds(ri, OK(ContentType, content_type), combo_tag) << strict << 
                  Page(ShowDocument(TOC(couched, id, ""), md, tweedy)).html
                )
            }
          case (NotModified.toInt, ri, _) => Some(cache_heds(ri, NotModified, 
            (couch_et, tweed, tweed_js) match {
              case (Some(couch_et), Some(tweed), Some(Search.id(latest) :: _)) => 
                ET(SpliceTag(DocTag(couch_et), tweed, latest.toString))
              case (Some(couch_et), _, _) => ET(DocTag(couch_et))
              case _ => ""
            }
          ) )
          case (NotFound.toInt, _, _) => None 
        }

      case Path(Index(db)) => Some(redirect(DbId(db, db(couch).all_docs.first)))
      case _ => None
    }
}
