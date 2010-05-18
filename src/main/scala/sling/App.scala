package sling

import dispatch._
import dispatch.Http._
import dispatch.couch._
import dispatch.json._
import dispatch.json.JsHttp._
import dispatch.twitter.Search

import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils

import net.lag.configgy.Configgy

import unfiltered.request._
import unfiltered.response._

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
  val http = new Http with Threads
  
/*  def cache_heds(ri: HttpResponse, ro: Response[Stream], combo_tag: String) = 
    ro(Date, ri.getFirstHeader(Date).getValue)(CacheControl, "max-age=600")(ETag, combo_tag) */
}

class App extends unfiltered.Plan({
  case Path(DbId(db, id), req) =>
    val IfNoneMatch = "If-None-Match"
    val ETag = "ETag"
    val (couch_et, tweed, tweed_js) =
      req.getHeader(IfNoneMatch) match {
        case ET(SpliceTag(couch_et, tweed, latest)) =>
          val res = App.http(Search(tweed))
          res.firstOption.filter { case Search.id(id) => id == latest } map { js =>
            (Some(couch_et), Some(tweed), Some(res))
          } getOrElse { (None, Some(tweed), Some(res)) }
        case ET(couch_et) =>
          (Some(couch_et), None, None)
        case _ => (None, None, None)
      }
    val with_heds = couch_et map { tag => /\ <:< Map(IfNoneMatch -> ET(tag)) } getOrElse /\
    (App.http x with_heds <& Doc(db, id)) {
      case (200, ri, Some(entity)) =>
        val ET(couch_et) = ri.getFirstHeader(ETag).getValue
        id match {
          case ("style.css") => 
            val PageDoc.body(css) = Js(entity.getContent)
            new StringResponder(css) with CssContent
          case (id) if req.getParameter("edit") != null =>
            //Some(cache_heds(ri, OK(ContentType, content_type), ET(couch_et))
            Page(EditDocument(TOC(db, App.http, id, "?edit"), 
              EntityUtils.toString(entity, "utf8")
            ))
          case (id) =>
            val js = Js(entity.getContent())
            val PageDoc.body(md) = js
            val (combo_tag, tweedy) = js match {
              case PageDoc.tweed(t) => 
                val ljs = tweed_js.getOrElse { App.http(Search(t)) }
                val ct: String = ljs.firstOption.map {
                  case Search.id(id) => ET(SpliceTag(couch_et, t, id))
                } getOrElse ET(couch_et)
                (ct, Some(t, ljs))
              case _ => ( ET(couch_et), None )
            }
            //Some(cache_heds(ri, OK(ContentType, content_type), combo_tag) << strict << 
            Page(ShowDocument(TOC(db, App.http, id, ""), md, tweedy))
        }
/*      case (NotModified.toInt, ri, _) => 
        Some(cache_heds(ri, NotModified, (couch_et, tweed, tweed_js) match {
          case (Some(couch_et), Some(tweed), Some(Search.id(latest) :: _)) => 
            ET(SpliceTag(couch_et, tweed, latest))
          case (Some(couch_et), _, _) => ET(couch_et)
          case _ => ""
        }
      ) )*/
      case (404, _, _) => Pass 
    }
/*
  case Path(Index(db), req) => (App.http x (Slouch menu_main db)).firstOption map { id =>
    redirect(DbId(db, id))
  } */
})
