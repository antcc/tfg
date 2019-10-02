/*
Distributed fuzzy cluster estimation method.
Antonio Coín.
*/

import scala.io.Source
import java.io.File
import java.io.PrintWriter
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf

object SparkFuzzyChiu {

  /********** Constants *************/

  val ra = 1
  val rb = 1.5 * ra
  val alpha = 4 / (ra * ra)
  val beta = 4 / (rb * rb)
  val lower_bound = 0.15
  val upper_bound = 0.5

  /********** Variables *************/

  var saveFile = true
  var fs_hdfs = false

  /********* Functions **********/

  def writeToFile(p: String, s: String): Unit = {
    val pw = new PrintWriter(new File(p))
    try pw.write(s) finally pw.close()
  }

  def resultToString(centers : List[List[Double]]) = {
    var result = "Centros:\n"
    for (center <- centers) {
      result += "("
      for (elem <- center)
        result += (elem + ", ")
      result += ")\n"
    }

    result += ("Longitud: " + centers.length + "\n")
    result
  }

  def distanceSquared(xs: List[Double], ys: List[Double]) = {
    (xs zip ys).map{ case (x,y) => math.pow(y - x, 2) }.sum
  }

  // TODO: cache() ?
  def main(args: Array[String]) = {
    // Configure spark
    val conf = new SparkConf().setAppName("FuzzyChiu")
    val sc = new SparkContext(conf)

    // Read file
    var inputFile = args.headOption.getOrElse {
      Console.err.println("error: se necesita un fichero de entrada\n")
      sys.exit(1)
    }
    if (fs_hdfs)
      inputFile = "file://" + inputFile

    // Load input file into RDD
    val input = sc.textFile(inputFile)
    val points = input.map(line => line.split(',').map(_.toDouble).toList)

    var centers = List[List[Double]]()
    val pairs = points.cartesian(points)

    // Compute initial potential
    var potential = pairs.map{ case (a,b) => (a,
                    math.exp(-alpha * distanceSquared(a, b)))}
                    .reduceByKey(_ + _)

    var chosenTuple = potential.max()(Ordering[Double].on(x => x._2))
    var firstCenterPotential = chosenTuple._2
    var numPoints = points.count()

    var stop = false
    var test = false // don't test first center
    while (!stop) {

      var chosenCenter = chosenTuple._1
      var chosenPotential = chosenTuple._2

      // Check stopping condition
      while (test) {
        // Accept and continue
        if (chosenPotential > upper_bound * firstCenterPotential) {
          centers = chosenCenter :: centers
          test = false
          if (centers.length >= numPoints)
            stop = true
        }

        // Reject and stop
        else if (chosenPotential < lower_bound * firstCenterPotential) {
          test = false
          stop = true
        }

        // Gray zone
        else {
          var dmin = centers.map {x => math.sqrt(distanceSquared(chosenCenter, x)) }
                     .reduceLeft(_ min _)

          // Accept and continue
          if (dmin / ra + chosenPotential / firstCenterPotential >= 1) {
            centers = chosenCenter :: centers
            test = false
            if (centers.length >= numPoints)
              stop = true
          }

          // Reject and re-test
          else {
            potential = potential.map{case (a,b) => (a,
                               {if (a == chosenCenter) 0.0 else b})}

            // Find new center
            chosenTuple = potential.max()(Ordering[Double].on(x => x._2))
            chosenCenter = chosenTuple._1
            chosenPotential = chosenTuple._2
          }
        }
      }

      // Revise potential of points
      potential = potential.map{case (a,b) => (a,
                          b - chosenPotential * math.exp(-beta * distanceSquared(a, chosenCenter)))}

      if (!stop) {
        // Find new center
        chosenTuple = potential.max()(Ordering[Double].on(x => x._2))
        chosenCenter = chosenTuple._1
        chosenPotential = chosenTuple._2
        test = true
      }
    }


    if (saveFile) {
      writeToFile("output/out.txt", resultToString(centers))
    }
    else {
      print(resultToString(centers))
    }

    // Stop spark
    sc.stop()
  }
}
