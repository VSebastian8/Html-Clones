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
import java.util.concurrent.TimeUnit

import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.StandardCopyOption
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions}
import org.openqa.selenium.{TimeoutException, WebDriver}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.openqa.selenium.{OutputType}
import scala.collection.mutable.HashMap

// Config variables
val tiers = List(1, 2, 3, 4)
val threads = 4
val alpha = 15
val max_wait = 60

@main def groupClones(): Unit =
  tiers.foreach(clusteringAlgorithm(_))

// Read file names from the specific tier
// Parse the html from each file
// Group the sites by the length of their content
// Write the groupings to the output file
// Screenshot each page in a group
def clusteringAlgorithm(tier: Int): Unit =
  println("Grouping clones for tier " + tier)
  // Read the files
  val files = readFiles(tier)
  // Clustering algorithm
  val groups = contentLengthClustering(files)
  // Write to output
  writeGroups(os.pwd / "output" / "txt" / ("tier" + tier + ".txt"), groups)
  // Create Selenium web driver
  val webDriver = createDriver()
  // Take screenshots for output
  pageScreenshots(webDriver, groups, tier)

// Read from the specified subdirectory
def readFiles(tier: Int): IndexedSeq[Path] =
  os.list(os.pwd / "input" / ("tier" + tier))

// Get the name of a File
def pathToName(path: Path): String =
  path.toString.split("/").last

// First algorithm
def contentLengthClustering(
    files: IndexedSeq[Path]
): List[(Int, List[Path])] =
  // Split the list over n threads
  val splitFiles =
    files.grouped((files.length.toDouble / threads).ceil.toInt).toList
  assert(
    splitFiles.map(_.length).sum == files.length,
    s"Splitting the list in $threads failed"
  )
  // Browser for parsing the HTML
  val browser: Browser = JsoupBrowser()
  // Parse the HTML for each page in each group, then calculate the length of the content
  val listFutures = splitFiles.map(group =>
    Future {
      // Tags that contain relevant content
      val contentTags =
        List("p", "a", "h1", "h2", "h3", "h4", "h5", "h6", "li", "span", "div")
      // Extract the content from each html page
      // Calculate the total length of the content per page
      LazyList
        .from(group)
        .map((p: Path) => (p, browser.parseFile(p.toString)))
        .map((path, doc) =>
          (path, contentTags.map(doc >> allText(_)).map(_.length()).sum())
        )
    }
  )
  // Await the future
  Await.result(
    Future
      .sequence(listFutures)
      .map((result: List[LazyList[(Path, Int)]]) =>
        // List of tuples (path, length) where path = html page and length = content in page
        groupByContent(result.flatten.sorted(Ordering.by(_(1))), alpha)
      ),
    max_wait.second
  )

// Group the pages by this length (within a procentage)
def groupByContent(
    docsContent: List[(Path, Int)],
    distance: Int
): List[(Int, List[Path])] =
  // list of (content_length, grouped_sites)
  var groups = List[(Int, List[Path])]()
  docsContent
    .foreach((path: Path, length: Int) => {
      // Either add the page to the beginning of the list or initialize a new group with that page
      // Since the content list is sorted, the page is either in the correct range for the first group or it starts a new group
      groups = groups match {
        case (target, group) :: rest
            if length < target + (target / distance) + 10 =>
          (length, path :: group) :: rest
        case _ => (length, List(path)) :: groups
      }
    })
  groups

// Write to the output file
def writeGroups(outputPath: Path, groups: List[(Int, List[Path])]): Unit =
  os.write.over(outputPath, "", createFolders = true)
  groups.foreach((length, group) => {
    os.write.append(outputPath, length + ": ")
    os.write
      .append(
        outputPath,
        group.foldRight("")((path: Path, acc: String) =>
          acc + pathToName(path) + "   "
        )
      )
    os.write.append(outputPath, "\n")
  })

// Web driver with no js
def createDriver(): FirefoxDriver =
  val options = new FirefoxOptions()
  options.addArguments("--headless")
  options.addArguments("--disable-javascript")
  new FirefoxDriver(options)

// Take screenshots of each page in each group
def pageScreenshots(
    driver: FirefoxDriver,
    groups: List[(Int, List[Path])],
    tier: Int
): Unit =

  // Reuse the driver for all screenshots
  groups.zip(LazyList.from(1)).foreach {
    case ((_, pages), index) => {
      pages.foreach((path: Path) => {
        val name = pathToName(path)
        takeScreenshot(
          driver,
          path,
          s"./output/screenshots/tier$tier/group$index/$name.png"
        )
      })
    }
  }
  driver.quit()

// Helper function to take website screenshot
def takeScreenshot(
    driver: FirefoxDriver,
    url: Path,
    outputPath: String
): Unit = {
  try {
    // Set a timeout for page load
    driver.manage().timeouts().pageLoadTimeout(10, TimeUnit.SECONDS)
    // Navigate to the URL
    driver.get(s"file://$url")
    // Wait for a maximum of 2 seconds
    val wait = new WebDriverWait(driver, 2)
    wait.until(
      ExpectedConditions.presenceOfElementLocated(
        org.openqa.selenium.By.tagName("body")
      )
    )

    val screenshot = driver.getScreenshotAs(OutputType.FILE)
    FileUtils.copyFile(screenshot, new File(outputPath))
  } catch {
    case e: TimeoutException =>
      println(s"Timeout occurred for $url. Skipping to the next page.")
    case e: Exception =>
      println(s"Error processing $url: ${e.getMessage}")
  }
}
