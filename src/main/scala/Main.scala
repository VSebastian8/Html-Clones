@main def groupClones(): Unit =
  SeleniumService.initialize()
  timed {
    config.tiers.foreach(ClusteringAlgorithm.run(_))
  }
  SeleniumService.close()
