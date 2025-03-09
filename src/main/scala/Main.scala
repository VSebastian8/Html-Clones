import net.ruippeixotog.scalascraper.browser.{JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._

import net.ruippeixotog.scalascraper.model._

import os.*
import net.ruippeixotog.scalascraper.browser.{Browser}
import net.ruippeixotog.scalascraper.model.Document

// Read file names from the specific tier
// Parse the html from each file
// Group the sites by the length of their content
// Write the groupings to the output file
@main def group_clones(): Unit =
  val tier = 2
  println("Grouping clones for tier " + tier)
  val files = os.list(os.pwd / "input" / ("tier" + tier))
  // Parse the HTML in each file
  val browser: Browser = JsoupBrowser()
  val docs =
    files
      .map(_.toString())
      .map((s: String) => (s.split("/").last, browser.parseFile(s)))
  val groups = group_content_lengths(docs, 15)
  write_groups(os.pwd / "output" / ("tier" + tier + ".txt"), groups)

// Extract the content from each html page
// Calculate the total length of the content per page
// Group the pages by this length (within a procentage)
def group_content_lengths(
    docs: IndexedSeq[(String, Document)],
    distance: Int
): List[(Int, List[String])] =
  // Tags that contain relevant content
  val content_tags =
    List("p", "a", "h1", "h2", "h3", "h4", "h5", "h6", "li", "span", "div")

  val contents_length =
    docs.map((name, doc) =>
      (name, content_tags.map(doc >> allText(_)).map(_.length()).sum())
    )

  // list of (content_length, grouped_sites)
  var groups = List[(Int, List[String])]()
  contents_length
    .sorted(Ordering.by(_(1)))
    .foreach((name: String, length: Int) => {
      // Either add the site to a list (if its length is close to another group) or initialize a new group with that site
      var done: Boolean = false
      groups = groups.map((target, group) =>
        if (
          length > target - (target / distance) - 1 && length < target + (target / distance) + 1
        ) {
          if (!done) {
            done = true; 
            (length, group :+ name)
          } else { (target, group) }
        } else { (target, group) }
      )
      if (!done) { groups = groups :+ (length, List(name)) }
    })
  groups

  // Write to the output file
def write_groups(path: os.Path, groups: List[(Int, List[String])]): Unit =
  os.write.over(path, "")
  groups.foreach((length, group) => {
    os.write.append(path, length + ": ")
    os.write
      .append(
        path,
        group.foldRight("")((s: String, acc: String) => acc + s + "   ")
      )
    os.write.append(path, "\n")
  })
