import net.ruippeixotog.scalascraper.browser.{JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.browser.{Browser}
import net.ruippeixotog.scalascraper.model.Document
import net.ruippeixotog.scalascraper.model._

import scala.concurrent.{Await, Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import java.util.concurrent.{Executors, LinkedBlockingQueue, TimeUnit}
import java.util.logging._

import scala.util.{Success, Failure}
import scala.collection.mutable.HashMap

import os.*
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.StandardCopyOption

import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions}
import org.openqa.selenium.{TimeoutException, WebDriver, OutputType, By}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.openqa.selenium.PageLoadStrategy

// Colors
val RED = "\u001B[31m"
val GREEN = "\u001B[32m"
val BLUE = "\u001B[34m"
val CYAN = "\u001B[36m"
val RESET = "\u001B[0m"

// Config variables
val tiers = List(1, 2, 3, 4)
val threads = 4
val alpha = 15
val max_wait = 60

// Thread pool
val threadPool =
  ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threads))
// One resource for each active thread
val driverQueue =
  new LinkedBlockingQueue[FirefoxDriver]()
// Logger
val logger = Logger.getLogger("org.openqa.selenium.remote.ErrorCodes")

@main def groupClones(): Unit =
  // Disable Selenium logging
  logger
    .setLevel(Level.WARNING)
  // Initialize driver queue
  (1 to threads)
    .foreach { _ =>
      driverQueue.put(createDriver())
    }
  // Clustering algorithm with screenshots for visualization
  tiers.foreach(clusteringAlgorithm(_))
  // Close all drivers
  driverQueue.forEach(_.quit())

// Read file names from the specific tier
// Parse the html from each file
// Group the sites by the length of their content
// Write the groupings to the output file
// Screenshot each page in a group
def clusteringAlgorithm(tier: Int): Unit =
  println(s"${RED}Grouping clones for tier $tier ${RESET}")
  // Read the files
  val files = readFiles(tier)
  val timeBeforeCluster = System.nanoTime;
  // Clustering algorithm
  val groups = contentLengthClustering(files)
  val timeAfterCluster = System.nanoTime;
  // Time for clustering algorithm in milliseconds
  val clusterTime = (timeAfterCluster - timeBeforeCluster) / 1_000_000
  println(s"[$GREEN$clusterTime ms$RESET]")
  // Write to output
  writeGroups(os.pwd / "output" / "txt" / ("tier" + tier + ".txt"), groups)
  val timeBeforeScreenshot = System.nanoTime;
  // Take screenshots for output
  pageScreenshots(groups, tier)
  val timeAfterScreenshot = System.nanoTime;
  val screenshotTime = (timeAfterScreenshot - timeBeforeScreenshot) / 1_000_000
  println(s"[$GREEN$screenshotTime ms$RESET]")

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
    }(threadPool)
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

// Selenium Firefox Web Driver
def createDriver(): FirefoxDriver =
  val options = new FirefoxOptions()
  // Run the driver detached
  options.addArguments("--headless")
  // Do not wait for javascript if possible (prevents timeout)
  options.setPageLoadStrategy(PageLoadStrategy.NONE)
  // Stop logging from Selenium
  System.setProperty(
    "webdriver.firefox.logfile",
    "/dev/null"
  )
  options.addPreference("devtools.console.stdout.content", false)
  options.setLogLevel(Level.SEVERE)
  // Create the driver
  val driver = new FirefoxDriver(options)
  // Configure the timeouts for the driver
  driver
    .manage()
    .timeouts()
    .pageLoadTimeout(1, TimeUnit.SECONDS)
  driver

// Take screenshots of each page in each group
def pageScreenshots(
    groups: List[(Int, List[Path])],
    tier: Int
): Unit =
  val total = groups.length
  println(s"${BLUE}Taking screenshots for $total groups in tier $tier ${RESET}")
  var done = 0
  // Wait for all futures to complete
  Await.result(
    Future.sequence(groups.zip(LazyList.from(1)).map {
      case ((_, pages), index) =>
        Future {
          // Take a driver from the queue, blocking if there are none
          val driver = driverQueue.take()
          pages.foreach((path: Path) => {
            val name = pathToName(path)
            takeScreenshot(
              driver,
              path,
              s"./output/screenshots/tier$tier/group$index/$name.png"
            )
          })
          // Return the WebDriver to the queue for reuse
          driverQueue.put(driver)
          // Print the progress
          this.synchronized {
            done += 1
            println(s"[$CYAN$done/$total$RESET]")
          }
        }(threadPool)
    }),
    Duration.Inf
  )

// Helper function to take website screenshot
def takeScreenshot(
    driver: FirefoxDriver,
    url: Path,
    outputPath: String
): Unit = {
  try {
    // Navigate to the URL
    driver.get(s"file://$url")
    // Wait for a maximum of 1 second
    val wait = new WebDriverWait(driver, 1)
    wait.until(
      ExpectedConditions.presenceOfElementLocated(
        By.tagName("body")
      )
    )
    // Take the screenshot and save it
    val screenshot = driver.getScreenshotAs(OutputType.FILE)
    FileUtils.copyFile(screenshot, new File(outputPath))
  } catch {
    case e: TimeoutException =>
      println(s"Timeout occurred for $url.")
    case e: Exception =>
      println(s"Error processing $url.")
  }
}
