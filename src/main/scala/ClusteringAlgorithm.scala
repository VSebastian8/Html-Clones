import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.model._

import os._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object ClusteringAlgorithm:
  // Tags that contain relevant content
  val contentTags =
    List("p", "a", "h1", "h2", "h3", "h4", "h5", "h6", "li", "span", "div")

  /** `Clustering algorithm` with screenshots for `visualization`
    *   - Read file names from the specific tier
    *   - Parse the html from each file
    *   - Group the sites by the length of their content
    *   - Write the groupings to the output file
    *   - Screenshot each page in a group
    */
  def run(tier: Int): Unit =
    println(s"${config.RED}Grouping clones for tier $tier ${config.RESET}")
    // Read the files
    val files = readFiles(tier)
    // Clustering algorithm
    val groups = timed { contentLengthClustering(files) }
    // Write to output
    writeGroups(
      os.pwd / "output" / "solution1" / "txt" / ("tier" + tier + ".txt"),
      groups
    )
    // Take screenshots for output
    timed {
      SeleniumService.pageScreenshots(
        groups,
        tier,
        "./output/solution1/screenshots"
      )
    }

  // First algorithm
  def contentLengthClustering(
      files: IndexedSeq[Path]
  ): List[List[Path]] =
    // Split the list over n threads
    val splitFiles = splitList(files, config.threads)
    assert(
      splitFiles.map(_.length).sum == files.length,
      s"Splitting the list in ${config.threads} failed"
    )
    // Browser for parsing the HTML
    val browser: Browser = JsoupBrowser()
    // Parse the HTML for each page in each group, then calculate the length of the content
    val listFutures = splitFiles.map(group =>
      Future {

        // Extract the content from each html page
        // Calculate the total length of the content per page
        LazyList
          .from(group)
          .map((p: Path) => (p, p.toString |> browser.parseFile))
          .map((path, doc) =>
            (path, contentTags.map(doc >> allText(_)).map(_.length).sum)
          )
      }
    )
    // Await the future
    Await.result(
      Future
        .sequence(listFutures)
        .map((result: List[LazyList[(Path, Int)]]) =>
          // List of tuples (path, length) where path = html page and length = content in page
          groupByContent(
            result.flatten.sorted(Ordering.by(_(1)))
          )
        ),
      Duration.Inf
    )

  // Group the pages by this length (within a procentage)
  def groupByContent(
      docsContent: List[(Path, Int)]
  ): List[List[Path]] =
    // list of (content_length, grouped_sites)
    var groups = List[(Int, List[Path])]()
    docsContent
      .foreach((path: Path, length: Int) => {
        // Either add the page to the beginning of the list or initialize a new group with that page
        // Since the content list is sorted, the page is either in the correct range for the first group or it starts a new group
        groups = groups match {
          case (target, group) :: rest
              if length < target + (target / config.contentDistance) + 10 =>
            (length, path :: group) :: rest
          case _ => (length, List(path)) :: groups
        }
      })
    groups.map((_, paths) => paths)
