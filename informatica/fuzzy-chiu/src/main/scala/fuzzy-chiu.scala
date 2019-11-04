/*
Distributed fuzzy cluster estimation method.
Antonio Coín Castro.
*/

import scala.io.Source
import java.io.File
import java.io.PrintWriter
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.HashPartitioner

object SparkFuzzyChiu {

  /********** Constants *************/

  val ra = 0.3
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
    var result = ""
    for (center <- centers) {
      for (i <- 0 to center.length - 1) {
        result += center(i)

        if (i != center.length - 1)
          result += ","
        else
          result += "\n"
      }
    }
    result
  }

  def distanceSquared(xs: List[Double], ys: List[Double]) = {
    (xs zip ys).map{ case (x,y) => math.pow(y - x, 2) }.sum
  }

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
    val indexed = points.zipWithIndex()
    val indexedPoints = indexed.map{case (k, v) => (v, k)}

    val numPoints = points.count()
    var centers = List[List[Double]]()

    println("----> LEÍDOS " + points.count + " PUNTOS\n")

    println("----> PAREJAS HECHAS\n")

    val pairs = points.cartesian(points)

    val indices = sc.parallelize(
                    for(i <- 0L until numPoints; j <- 0L until numPoints; if i > j) yield (i, j))
    val joined = indices.join(indexedPoints).map {case (i, (j, v)) => (j, (i, v))}
    val d1 = joined.join(indexedPoints).map {case (j, ((i, v1), v2)) => (i, (v1, v2))}
    val d2 = joined.join(indexedPoints).map {case (j, ((i, v1), v2)) => (j, (v1, v2))}
    val dist1 = d1.mapValues{case (v1, v2) => math.exp(-alpha * distanceSquared(v1, v2))}.
                reduceByKey(_ + _)
    val dist2 = d2.mapValues{case (v1, v2) => math.exp(-alpha * distanceSquared(v1, v2))}.
                            reduceByKey(_ + _)
    val pot = dist1.join(dist2).mapValues{x => x.productIterator.toList.map{y => y.asInstanceOf[Double]}.sum + 1}


    println("-----> COSAS\n")

    var b = dist1.collect()
    for(p <- b) {
      println(p)
    }
    var c = dist2.collect()
    for(p <- b) {
      println(p)
    }

    // Compute initial potential
    var potential = pairs.map{ case (a,b) => (a,
                    math.exp(-alpha * distanceSquared(a, b)))}
                    .partitionBy(new HashPartitioner(101))
                    .reduceByKey(_ + _)
                    .cache()

    /**println("POTENCIALES INICIALES:")
    var h = potential.collect()
    for (i <- h)
      println(i._2)*/

    var chosenTuple = potential.max()(Ordering[Double].on(x => x._2))
    var chosenCenter = chosenTuple._1
    var firstCenterPotential, chosenPotential = chosenTuple._2

    println("\nPOTENCIAL ELEGIDO: " + chosenPotential)

    // First center
    centers = chosenCenter :: centers

    var stop = false
    var test = true
    while (!stop) {
      // Revise potential of points
      potential = potential.map{case (a,b) => (a,
                    b - chosenPotential * math.exp(-beta * distanceSquared(a, chosenCenter)))}.cache()

      // Find new center
      chosenTuple = potential.max()(Ordering[Double].on(x => x._2))
      chosenCenter = chosenTuple._1
      chosenPotential = chosenTuple._2
      test = true
      println("POTENCIAL ELEGIDO: " + chosenPotential)

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
          if ((dmin / ra) + (chosenPotential / firstCenterPotential) >= 1) {
            centers = chosenCenter :: centers
            test = false
            if (centers.length >= numPoints)
              stop = true
          }

          // Reject and re-test
          else {
            potential = potential.map{case (a,b) => (a,
                              {if (a == chosenCenter) 0.0 else b})}.cache()

            // Find new center
            chosenTuple = potential.max()(Ordering[Double].on(x => x._2))
            chosenCenter = chosenTuple._1
            chosenPotential = chosenTuple._2
          }
        }
      }
    }

    println("\n----> NÚMERO DE CENTROS ENCONTRADOS: " + centers.length)

    if (saveFile) {
      writeToFile("output/out_denorm.txt", resultToString(centers))
    }
    else {
      print(resultToString(centers))
    }

    // Stop spark
    sc.stop()
  }
}
