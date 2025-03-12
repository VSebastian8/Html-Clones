import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.openqa.selenium.{
  TimeoutException,
  WebDriver,
  OutputType,
  By,
  PageLoadStrategy,
  Dimension
}

import org.apache.commons.io.FileUtils
import java.io.File
import java.util.logging._
import os.Path

import scala.concurrent.{Await, Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.{LinkedBlockingQueue, Executors, TimeUnit}
import scala.concurrent.duration._

object SeleniumService:
  // Thread pool
  val threadPool =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threads))
  // One resource for each active thread
  val driverQueue =
    new LinkedBlockingQueue[FirefoxDriver]()
  // Logger that ignores Selenium js errors
  val logger = Logger.getLogger("org.openqa.selenium.remote.ErrorCodes")

  def initialize(): Unit = {
    // Disable Selenium logging
    logger
      .setLevel(Level.WARNING)
    // Initialize driver queue
    (1 to config.threads)
      .foreach { _ =>
        driverQueue.put(createDriver())
      }
  }

  def close(): Unit = {
    // Close all drivers
    driverQueue.forEach(_.quit())
  }

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
    // Create the driver
    val driver = new FirefoxDriver(options)
    // Configure the timeouts for the driver
    driver
      .manage()
      .timeouts()
      .pageLoadTimeout(config.timeout, TimeUnit.SECONDS)

    // Set initial window size
    driver.manage().window().setSize(new Dimension(1800, 900))

    // Calculate chrome height
    val windowHeight = driver.manage().window().getSize.height
    val viewportHeight =
      driver.executeScript("return window.innerHeight").asInstanceOf[Long]
    val firefoxHeight = windowHeight - viewportHeight

    // Adjust window size to account for chrome
    val targetViewportHeight = 900
    val adjustedWindowHeight = targetViewportHeight + firefoxHeight.toInt
    driver.manage().window().setSize(new Dimension(1800, adjustedWindowHeight))
    driver

  // Take screenshots of each page in each group
  def pageScreenshots(
      groups: List[List[Path]],
      tier: Int,
      outputDir: String
  ): Unit =
    val total = groups.length
    println(
      s"${config.BLUE}Taking screenshots for $total groups in tier $tier ${config.RESET}"
    )
    var done = 0
    // Wait for all futures to complete
    Await.result(
      Future.sequence(groups.zip(LazyList.from(1)).map { case (pages, index) =>
        Future {
          // Take a driver from the queue, blocking if there are none
          val driver = driverQueue.take()
          pages.foreach((path: Path) => {
            takeScreenshot(
              driver,
              path,
              s"$outputDir/tier$tier/group$index/${pathToName(path)}.png"
            )
          })
          // Return the WebDriver to the queue for reuse
          driverQueue.put(driver)
          // Print the progress
          this.synchronized {
            done += 1
            println(s"[${config.CYAN}$done/$total${config.RESET}]")
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
