package sling

import scala.xml.{Unparsed, Elem, NodeSeq}

import dispatch._
import dispatch.couch._
import dispatch.json._
import dispatch.twitter.Search

trait Press { val html: Elem }

case class Page(content: Content) extends Press {
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
trait Content { def head: NodeSeq; def body: Elem }

trait TitledContent extends Content {
  val title: String
  def head: NodeSeq = <title> { title } </title>
}

case class TOC(db: Database#H, curr_id: String, query: String) extends Press {
  lazy val html = <h4><ul class="toc">
    {
      db.all_docs map {
        case "style.css" => Nil
        case `curr_id` => <li> { curr_id } </li>
        case id => <li> <a href={ DbId(db, id) + query }>{ id }</a> </li> 
      }
    }
  </ul></h4>
}

trait Document extends TitledContent {
  val toc: TOC
  lazy val title = toc.db.name.capitalize + " → " + toc.curr_id
}

case class ShowDocument(toc: TOC, md: String, tweedy: Option[(String, List[JsValue])]) extends Document {
  override def head = super.head ++ tweedy.map { case (tweed, _) => 
    <link title="Atom 1.0 Feed" rel="alternate" type="application/atom+xml" href={
      "http://search.twitter.com/search.atom?" + Http ? Map("q" -> tweed) }/>
  }
	
  def body =
    <div id="content">
      <div class="container">
        <h2>{ title }</h2>
        { toc.html }
        { Unparsed((new js.showdown).makeHtml(md).toString) } 
        {
          tweedy map { case (tweed, js) =>
            <div>
              <h3>{ tweed } tweed</h3>
              <ul class="tweed"> {
                js map { js =>
                  val Search.text(text) = js
                  val Search.from_user(from) = js
                  val Search.created_at(time) = js
                  val Search.id(id) = js
                  val from_pg = "http://twitter.com/" + from
                  <li>
                    <a href={ from_pg }>{ from }</a>:
                    { Unparsed(text) }
                    <div>
                      <em> { time.replace(" +0000", "") } </em>
                      <a href={ "http://twitter.com/home?" + Http ? Map(
                          "status" -> ("@" + from + " " + tweed + " "), "in_reply_to_status_id" -> id, "in_reply_to" -> from
                        ) }>Reply</a>
                      <a href={ from_pg + "/statuses/" + id }>View Tweet</a>
                    </div>
                  </li>
                }
              } </ul>
              <p>
                <a href={ "http://search.twitter.com/search?" + Http ? Map("q" -> tweed) }>
                  See all Twitter Search results for { tweed }
                </a>
              </p>
            </div>
          } toList
        }
      </div>
    </div>
}

case class EditDocument(toc: TOC, md: String) extends Document {
  override def head = super.head ++ (
    <link rel="stylesheet" href="/css/edit.css" type="text/css" media="screen" /> 
    <script type="text/javascript" src="/script/jquery.js"></script>
    <script type="text/javascript" src="/script/json2.js"></script>
    <script type="text/javascript" src="/js/wmd/showdown.js"></script>
    <script type="text/javascript" src="/js/edit.js"></script>
    <script type="text/javascript"> 
      { Unparsed(
          "var doc = " + md + ";" +
          "var doc_url = '/couch" + DbId(toc.db, toc.curr_id) + "';"
      ) }
    </script>
  )

  def body =
    <div>
      <div id="edit"><div id="fixed"><div>
        <div class="container">
          <form id="form">
            <div><textarea id="body" name="body"></textarea></div>
            <div><input type="submit" value="Save Changes" /></div>
          </form>
        </div>
      </div></div></div>
      <div id="content">
        <div class="container">
          <img id="shade" title="Toggle Editor" src="/css/ship-up.gif" />
          { toc.html }
          <div id="body-preview"></div>
        </div>
      </div>
    </div>
}
