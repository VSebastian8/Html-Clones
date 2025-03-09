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
  val tier = 1
  println("Grouping clones for tier " + tier)
  val files = os.list(os.pwd / "input" / ("tier" + tier))
  // Parse the HTML in each file
  val browser: Browser = JsoupBrowser()
  val docs = LazyList.from(files)
      .map(_.toString())
      .map((s: String) => (s.split("/").last, browser.parseFile(s)))
  val groups = group_content_lengths(docs, 15)
  write_groups(os.pwd / "output" / ("tier" + tier + ".txt"), groups)

// Extract the content from each html page
// Calculate the total length of the content per page
// Group the pages by this length (within a procentage)
def group_content_lengths(
    docs: LazyList[(String, Document)],
    distance: Int
): List[(Int, List[String])] =
  // Tags that contain relevant content
  val content_tags =
    List("p", "a", "h1", "h2", "h3", "h4", "h5", "h6", "li", "span", "div")

  val docs_content_lengths: LazyList[(String, Int)] =
    docs.map((name, doc) =>
      (name, content_tags.map(doc >> allText(_)).map(_.length()).sum())
    )

  // list of (content_length, grouped_sites)
  var groups = List[(Int, List[String])]()
  docs_content_lengths
    .sorted(Ordering.by(_(1)))
    .foreach((name: String, length: Int) => {
      // Either add the page to the beginning of the list or initialize a new group with that page 
      // Since the content list is sorted, the page is either in the correct range for the first group or it starts a new group
      groups = groups match {
        case (target, group) :: rest if
          length < target + (target / distance) + 10
          => (length, name :: group) :: rest 
        case _ => (length, List(name)) :: groups 
      }})
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
