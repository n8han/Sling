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
import dispatch.json.JsHttp._
import dispatch.twitter.Search

import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils

import net.lag.configgy.Configgy

object Slouch {
  def apply() = Couch(
    Configgy.config.getString("couch.host","127.0.0.1"),
    Configgy.config.getInt("couch.port",5984)
  )
  import Js._
  /** Get menu, create view if not present in db */
  def menu_main(db: Db) = {
    val menu = Design(db, "menu")
    val main = View(menu, "main") ># Couch.id_rows
    main {
      case (404, _, _, _) =>
        val h2 = new Http
        h2(menu <<< ( Design.views <<| ('main <<| (View.map <<| 
          "function(doc) { if (doc.menu && doc.menu.main_idx) emit(doc.menu.main_idx, null) }"
        ))) >| )
        h2(main)
      case (_, _, _, out) => out()
    }
  }
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
    case Re(db, id) => Some(new Db(Slouch(), db), to_id(id))
    case _ => None
  }
  def apply(db: Db, id: String) = 
    "/" + (db.name :: to_path(id) :: Nil).mkString("/")
}
object Index {
  val Re =  "^/([a-z_]+)/?$".r
  def unapply(path: String) = path match { 
    case Re(db) => Some(Db(Slouch(), db)) 
    case _ => None
  }
}
// extract etag between quotes and discard any gzip addition
// (https://issues.apache.org/bugzilla/show_bug.cgi?id=39727)
object ET extends util.matching.Regex("\"(.*)\"(?:-gzip)?") {
  def apply(tag: String) = '"' + tag + '"'
}

object SpliceTag {
  def unapply(tag: String) = tag.split('|') match {
    case Seq(couch_et, tweed, latest) => Some((couch_et, tweed, BigDecimal(latest)))
    case _ => None
  }
  def apply(couch_et: String, tweed: String, latest: BigDecimal) = 
    List(couch_et, tweed, latest).mkString("|")
}

object App {
  import Js._
  import Http._
  implicit val charSet = UTF8
  def content_type = "text/html; charset=UTF-8"
  
  def cache_heds(ri: HttpResponse, ro: Response[Stream], combo_tag: String) = 
    ro(Date, ri.getFirstHeader(Date).getValue)(CacheControl, "max-age=600")(ETag, combo_tag)
}

class App extend unfiltered.Handler({
  case Path(DbId(db, id), req) =>
    val IfNoneMatch = "If-None-Match"
    val (couch_et, tweed, tweed_js) =
      request.headers.find {
        case (k, v) => k.asString == IfNoneMatch
      } map { _._2.mkString } map {
        case ET(SpliceTag(couch_et, tweed, latest)) =>
          val res = http(Search(tweed))
          res.firstOption.filter { case Search.id(id) => id == latest } map { js =>
            (Some(couch_et), Some(tweed), Some(res))
          } getOrElse { (None, Some(tweed), Some(res)) }
        case ET(couch_et) =>
          (Some(couch_et), None, None)
        case _ => (None, None, None)
      } getOrElse (None, None, None)
    val with_heds = couch_et map { tag => /\ <:< Map(IfNoneMatch -> ET(tag)) } getOrElse /\
    (http x with_heds <& Doc(db, id)) {
      case (OK.toInt, ri, Some(entity)) =>
        val ET(couch_et) = ri.getFirstHeader(ETag).getValue
        id match {
          case ("style.css") =>
            Some(cache_heds(ri, OK(ContentType, "text/css; charset=UTF-8"), ET(couch_et)) << 
              PageDoc.body(Js(entity.getContent())).toList
            )
          case (id) if request !? "edit" =>
            Some(cache_heds(ri, OK(ContentType, content_type), ET(couch_et)) << strict << 
              Page(EditDocument(TOC(db, http, id, "?edit"), 
                EntityUtils.toString(entity, UTF8)
              )).html
            )
          case (id) =>
            val js = Js(entity.getContent())
            val PageDoc.body(md) = js
            val (combo_tag, tweedy) = js match {
              case PageDoc.tweed(t) => 
                val ljs = tweed_js.getOrElse { http(Search(t)) }
                val ct: String = ljs.firstOption.map {
                  case Search.id(id) => ET(SpliceTag(couch_et, t, id))
                } getOrElse ET(couch_et)
                (ct, Some(t, ljs))
              case _ => ( ET(couch_et), None )
            }
            Some(cache_heds(ri, OK(ContentType, content_type), combo_tag) << strict << 
              Page(ShowDocument(TOC(db, http, id, ""), md, tweedy)).html
            )
        }
      case (NotModified.toInt, ri, _) => Some(cache_heds(ri, NotModified, 
        (couch_et, tweed, tweed_js) match {
          case (Some(couch_et), Some(tweed), Some(Search.id(latest) :: _)) => 
            ET(SpliceTag(couch_et, tweed, latest))
          case (Some(couch_et), _, _) => ET(couch_et)
          case _ => ""
        }
      ) )
      case (NotFound.toInt, _, _) => None 
    }

  case Path(Index(db)) => (http x (Slouch menu_main db)).firstOption map { id =>
    redirect(DbId(db, id))
  }
})
