import net.ruippeixotog.scalascraper.browser.{JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.browser.{Browser}
import net.ruippeixotog.scalascraper.model.Document

import net.ruippeixotog.scalascraper.model._

import os.*
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Success
import scala.util.Failure

// Config variables
val tier = 1
val threads = 4
val alpha = 15
val max_wait = 60

// Read file names from the specific tier
// Parse the html from each file
// Group the sites by the length of their content
// Write the groupings to the output file
@main def group_clones(): Unit =
  println("Grouping clones for tier " + tier)
  // Read the files
  val files = read_files(tier)
  // Clustering algorithm
  val groups = content_length_clustering(files)
  // Write to output
  write_groups(os.pwd / "output" / ("tier" + tier + ".txt"), groups)

// Read from the specified subdirectory
def read_files(tier: Int): IndexedSeq[String] =
  os.list(os.pwd / "input" / ("tier" + tier)).map(_.toString())

// First algorithm
def content_length_clustering(
    files: IndexedSeq[String]
): List[(Int, List[String])] =
  // Split the list over n threads
  val split_files =
    files.grouped((files.length.toDouble / threads).ceil.toInt).toList
  assert(
    split_files.map(_.length).sum == files.length,
    s"Splitting the list in $threads failed"
  )
  // Parse the HTML for each page in each group, then calculate the length of the content
  val list_futures = split_files.map(group =>
    Future {
      // Browser for parsing the HTML
      val browser: Browser = JsoupBrowser()
      // Tags that contain relevant content
      val content_tags =
        List("p", "a", "h1", "h2", "h3", "h4", "h5", "h6", "li", "span", "div")
        // Extract the content from each html page
        // Calculate the total length of the content per page
      LazyList
        .from(group)
        .map((s: String) => (s.split("/").last, browser.parseFile(s)))
        .map((name, doc) =>
          (name, content_tags.map(doc >> allText(_)).map(_.length()).sum())
        )
    }
  )
  // Await the future
  Await.result(
    Future
      .sequence(list_futures)
      .map((result: List[LazyList[(String, Int)]]) =>
        // List of tuples (name, length) where name = html page and length = content in page
        group_by_content(result.flatten.sorted(Ordering.by(_(1))), alpha)
      ),
    max_wait.second
  )

// Group the pages by this length (within a procentage)
def group_by_content(
    docs_content: List[(String, Int)],
    distance: Int
): List[(Int, List[String])] =
  // list of (content_length, grouped_sites)
  var groups = List[(Int, List[String])]()
  docs_content
    .foreach((name: String, length: Int) => {
      // Either add the page to the beginning of the list or initialize a new group with that page
      // Since the content list is sorted, the page is either in the correct range for the first group or it starts a new group
      groups = groups match {
        case (target, group) :: rest
            if length < target + (target / distance) + 10 =>
          (length, name :: group) :: rest
        case _ => (length, List(name)) :: groups
      }
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
