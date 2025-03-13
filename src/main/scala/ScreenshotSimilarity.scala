import os._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object ScreenshotSimilarity:

  /** `Screenshot Similarity` with `visualization`
    *   - Read file names from the specific tier
    *   - Screenshot each html page, holding a list of the ones that time out
    *   - Split the image in 10x10 pixel squares
    *   - Take the average color for each square
    *   - Compare two images by calculating the sum of their color diferences
    *   - Group the images if their sum is low enough
    *   - Visualize the images
    */
  def run(tier: Int): Unit =
    println(s"${config.RED}Grouping clones for tier $tier ${config.RESET}")
    // Read the files
    val files = readFiles(tier)
    // Clustering algorithm
    val groups = timed { imageColorSimilarity(files) }
    // Write to output
    writeGroups(
      os.pwd / "output" / "solution2" / "txt" / ("tier" + tier + ".txt"),
      groups
    )
    // Take screenshots for output
    timed {
      SeleniumService.visualize(
        groups,
        tier,
        "./output/solution2/screenshots"
      )
    }

  def imageColorSimilarity(files: IndexedSeq[Path]): List[List[Path]] =
    // Split the list over n threads
    val splitFiles = splitList(files, config.threads)
    assert(
      splitFiles.map(_.length).sum == files.length,
      s"Splitting the list in ${config.threads} failed"
    )

    // Parse the HTML for each page in each group, then calculate the length of the content
    val listFutures = LazyList
      .from(splitFiles)
      .map(group =>
        Future {
          // Extract the content from each html page and calculate its length
          val (images, timedOut) = SeleniumService.capture(LazyList.from(group))
          (
            images.map { case (path, image) =>
              (path, colorAverage(image))
            },
            timedOut
          )
        }
      )
    // Await the futures
    Await.result(
      Future
        .sequence(listFutures)
        .map {
          case result => {
            val (imageList, timeoutList) = result.unzip
            (imageList.flatten, timeoutList.flatten)
          }
        }
        .map { case (images, timedOut) =>
          timedOut.toList :: (groupByColor(images))
        },
      Duration.Inf
    )

  def tripletSum(t1: (Int, Int, Int), t2: (Int, Int, Int)): (Int, Int, Int) =
    (t1, t2) match {
      case ((x1, y1, z1), (x2, y2, z2)) =>
        (x1 + x2, y1 + y2, z1 + z2)
    }

  // Group the image into 10x10 squares and take their average
  def colorAverage(
      image: List[List[(Int, Int, Int)]]
  ): List[(Int, Int, Int)] =
    val simplifiedMat = image
      .grouped(10)
      .map {
        case groupX => {
          groupX
            .map { case groupY =>
              groupY
                .grouped(10)
                .map(_.foldRight((0, 0, 0))(tripletSum))
                .toList
            }
            .transpose
            .map(_.foldRight(0, 0, 0)(tripletSum))
            .map((x, y, z) => (x / 100, y / 100, z / 100))
        }
      }
      .toList
    simplifiedMat.flatten()

  // Mean Squared Error for two images
  def imageComparison(
      image1: List[(Int, Int, Int)],
      image2: List[(Int, Int, Int)]
  ): Int =
    image1.zip(image2).foldRight(0) {
      case (((a1, b1, c1), (a2, b2, c2)), acc) => {
        acc
          + (a1 - a2) * (a1 - a2)
          + (b1 - b2) * (b1 - b2)
          + (c1 - c2) * (c1 - c2)
      }
    }

  def averageImage(
      image: List[(Int, Int, Int)],
      previousAvg: List[(Int, Int, Int)],
      n: Int
  ): List[(Int, Int, Int)] =
    image.zip(previousAvg).map { case ((a1, b1, c1), (a2, b2, c2)) =>
      (
        a1 / n + a2 * (n - 1) / n,
        b1 / n + b2 * (n - 1) / n,
        c1 / n + c2 * (n - 1) / n
      )
    }

  // Compare the mean squared error of two images with the acceptable margin
  def groupByColor(
      pages: LazyList[(Path, List[(Int, Int, Int)])]
  ): List[List[Path]] =
    // Holds the average image and the images that make it up
    var groups = List[(List[(Int, Int, Int)], List[Path])]()
    pages.foreach {
      case (path, image) => {
        var done = false
        groups = groups.map {
          case (avgImage, paths) => {
            if (done == false) {
              val mse = imageComparison(image, avgImage) / (190 * 90)
              if (mse < config.imageDistance) {
                done = true
                (averageImage(image, avgImage, paths.length + 1), path :: paths)
              } else {
                (avgImage, paths)
              }
            } else {
              (avgImage, paths)
            }
          }
        }
        if (done == false) {
          groups = (image, List(path)) :: groups
        }
      }
    }
    groups.map((_, paths) => paths)
