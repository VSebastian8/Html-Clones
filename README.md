# Html-Clones

## Task

Design an algorithm that will group together HTML documents which are similar from the perspective of a user who opens them in a web browser.

## Content Length Clustering

This first approached was based on the ideea that clones generally have the same content, with minimal modifications. With the use of a HTML Parsing library, the algorithm extracts the text content from relevant tags (<a\>, <p\>, <h1\>, and so on). Then we simply calculate the length of this content for each page and group pages with simillar content lengths (where their difference is less than 15%).

## First Results

## Screenshot Convolution

## Final Results

## Scalability

In Scala, we can easily improve scalability by using `lazy evaluation` in terms of **LazyList**. When working large amounts of data, we can avoid unnecessary computation and reduce memory usage.

In this case, we would not want to hold a list with all of the parsed html files in memory. We would like to take a single file, process it, free any memory no longer needed, and then finally move on to the next file. By using a lazy list, we can achieve a similar behaviour while still writing the code in a nice, easy to read, `functional` style.

Another idea here is to take advantage of `parallelism`, using multiple threads for processing the list of files in the target directory. Scala offers effortless `concurrency` through **Futures**. The clustering algorithm parses the html and calculates the length of the content in such a list of futures, that are then joined and awaited.
