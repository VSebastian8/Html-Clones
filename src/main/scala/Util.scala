import os.*

// Read from the specified subdirectory
def readFiles(tier: Int): IndexedSeq[Path] =
  os.list(os.pwd / "input" / ("tier" + tier))

// Get the name of a File
def pathToName(path: Path): String =
  path.toString.split("/").last

// Split a list in num ways
def splitList[A](list: IndexedSeq[A], num: Int): List[IndexedSeq[A]] =
  list.grouped((list.length.toDouble / config.threads).ceil.toInt).toList

// Write the groups to the output file
def writeGroups(outputPath: Path, groups: List[List[Path]]): Unit =
  os.write.over(outputPath, "", createFolders = true)
  groups.zip(LazyList.from(1)) foreach ((group, index) => {
    os.write.append(outputPath, "group " + index + ": ")
    os.write
      .append(
        outputPath,
        group.foldRight("")((path: Path, acc: String) =>
          acc + pathToName(path) + "   "
        )
      )
    os.write.append(outputPath, "\n")
  })

// Basic decorator for timing a function
def timed[R](block: => R): R = {
  val timeBefore = System.nanoTime;
  // Call to the function
  val result = block
  // Benchmark
  val timeAfter = System.nanoTime;
  val totalTime = (timeAfter - timeBefore) / 1_000_000
  println(s"[${config.GREEN}$totalTime ms${config.RESET}]")
  // Return the result of the function
  result
}
