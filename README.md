# Html-Clones

## Task

Design an algorithm that will group together HTML documents which are similar from the perspective of a user who opens them in a web browser.

## Content Length Clustering

This first approached was based on the ideea that clones generally have the same content, with minimal modifications. With the use of a HTML Parsing library, the algorithm extracts the text content from relevant tags (<a\>, <p\>, <h1\>, and so on). Then we simply calculate the length of this content for each page and group pages with simillar content lengths (where their difference is less than 15%).

## First Results

## Screenshot Convolution

## Final Results

## Scalability
