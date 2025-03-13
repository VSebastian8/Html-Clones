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
    val listFutures = splitFiles.map(group =>
      Future {
        // Extract the content from each html page and calculate its length
        val (images, timedOut) = SeleniumService.capture(List.from(group))
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
          timedOut :: (groupByColor(images))
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

  def groupByColor(
      pages: List[(Path, List[(Int, Int, Int)])]
  ): List[List[Path]] = ???
