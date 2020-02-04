/*
Distributed fuzzy cluster estimation method.
Antonio Coín Castro.
*/

import scala.io.Source
import scala.math.{pow, abs, sqrt}
import java.io.File
import java.io.PrintWriter
import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.rdd.RDD
import org.apache.spark.HashPartitioner
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import breeze.linalg.DenseVector

object FuzzyChiu {

  /********** Constants *************/

  val ra = 0.3
  val rb = 1.5 * ra
  val alpha = 4 / (ra * ra)
  val beta = 4 / (rb * rb)
  val lower_bound = 0.15
  val upper_bound = 0.5

  val m = 2
  val eps = 1e-6
  val max_iter = 200

  val saveFile = true
  val fs_hdfs = false
  val local = false

  /********* Helper Functions **********/

  def writeToFile(p: String, s: String): Unit = {
    val pw = new PrintWriter(new File(p))
    try pw.write(s) finally pw.close()
  }

  def resultToString(centers : List[Vector]) = {
    var result = ""
    for (center <- centers) {
      for (i <- 0 to center.size - 1) {
        result += center(i)

        if (i != center.size - 1)
          result += ","
        else
          result += "\n"
      }
    }
    result
  }

  /********* Chiu Variants **********/

  def globalChiu(points : RDD[Vector]): List[Vector] = {
    val numPoints = points.count()
    var centers = List[Vector]()
    val pairs = points.cartesian(points)

    // Compute initial potential
    var potential = pairs.map{case (a,b) => (a,
                    math.exp(-alpha * Vectors.sqdist(a, b)))}
                    .partitionBy(new HashPartitioner(101))
                    .reduceByKey(_ + _)
                    .cache()

    var chosenTuple = potential.max()(Ordering[Double].on(x => x._2))
    var chosenCenter = chosenTuple._1
    var firstCenterPotential, chosenPotential = chosenTuple._2

    // First center
    centers = chosenCenter :: centers

    var stop = false
    var test = true
    while (!stop) {
      // Revise potential of points
      potential = potential.map{case (a,b) => (a,
                    b - chosenPotential * math.exp(-beta * Vectors.sqdist(a, chosenCenter)))}.cache()

      // Find new center
      chosenTuple = potential.max()(Ordering[Double].on(x => x._2))
      chosenCenter = chosenTuple._1
      chosenPotential = chosenTuple._2
      test = true

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
          var dmin = centers.map{x => sqrt(Vectors.sqdist(chosenCenter, x))}
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

    return centers
  }

  def localChiu(it: Iterator[Vector]): Iterator[Vector] = {
    var centers = List[Vector]()
    var potential = List[(Vector, Double)]()
    var numPoints = 0
    var (it1, it2) = it.duplicate

    while (it1.hasNext) {
      var dup = it2.duplicate
      it2 = dup._1
      var it3 = dup._2
      var cur = it1.next
      var dist = 0.0

      while(it3.hasNext) {
        var aux : Vector = it3.next
        dist = dist + math.exp(-alpha * Vectors.sqdist(cur, aux))
      }

      potential = (cur, dist) :: potential
      numPoints = numPoints + 1
    }

    println("\n--------------> PARTICIÓN CON " + numPoints + " PUNTOS\n")

    var chosenTuple = potential maxBy { _._2 }
    var chosenCenter = chosenTuple._1
    var firstCenterPotential, chosenPotential = chosenTuple._2

    //println("\n--------------> POTENCIAL ELEGIDO: " + chosenPotential)

    // First center
    centers = chosenCenter :: centers

    var stop = false
    var test = true
    while (!stop) {
      // Revise potential of points
      potential = potential.map{case (a,b) => (a,
                    b - chosenPotential *
                    math.exp(-beta * Vectors.sqdist(a, chosenCenter)))}

      // Find new center
      chosenTuple = potential maxBy { _._2 }
      chosenCenter = chosenTuple._1
      chosenPotential = chosenTuple._2
      test = true

      //println("\n--------------> POTENCIAL ELEGIDO: " + chosenPotential)

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
          var dmin = centers.map{x => sqrt(Vectors.sqdist(chosenCenter, x))}
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
                              {if (a == chosenCenter) 0.0 else b})}

            // Find new center
            chosenTuple = potential maxBy { _._2 }
            chosenCenter = chosenTuple._1
            chosenPotential = chosenTuple._2

            //println("\n--------------> POTENCIAL ELEGIDO: " + chosenPotential)
          }
        }
      }
    }

    println("\n--------------> PARTICIÓN CON " + centers.length + " CENTROS\n")

    centers.iterator
  }

  /** FUZZY C MEANS **/

  def add(x: Vector, y: Vector) = {
    var bx = new DenseVector(x.toArray)
    var by = new DenseVector(y.toArray)
    Vectors.dense((bx + by).toArray)
  }

  def substract(x: Vector, y: Vector) = {
    var bx = new DenseVector(x.toArray)
    var by = new DenseVector(y.toArray)
    Vectors.dense((bx - by).toArray)
  }

  def multiply(x: Vector, l: Double) = {
    var bx = new DenseVector(x.toArray)
    Vectors.dense((l * bx).toArray)
  }

  def vabs(x: Vector) = {
    Vectors.dense(x.toArray.map(l => abs(l)))
  }

  def computeMatrix(x: Vector, c: Array[Vector]) = {
    var numCluster = c.length
    var membership = Array.ofDim[Double](numCluster)
    var distances = Array.ofDim[Double](numCluster)

    for (j <- 0 to numCluster - 1) {
      val denom = c.map {ck =>
        pow(sqrt(Vectors.sqdist(x, c(j))) / sqrt(Vectors.sqdist(x, ck)), 2.0 / (m - 1))
      }.sum

      if (denom.isInfinity)
        membership(j) = 0.0
      else if (denom.isNaN)
        membership(j) = 1.0
      else
        membership(j) = 1.0 / denom
    }

    (x, Vectors.dense(membership))
  }

  def stopping_cond(old: RDD[(Vector, Vector)], curr: RDD[(Vector, Vector)]): Boolean = {
    var diff = old.join(curr)
        .map{case(x, (u, v)) =>
          {
            var t = vabs(substract(v, u))
            t(t.argmax)
          }
        }
        .max()

    diff < eps
  }

  // Return loss function
  def fuzzyCMeans(points : RDD[Vector], init_centers : List[Vector]): (List[Vector], RDD[(Vector, Vector)], Double, Double, Int) = {
    val numCluster = init_centers.length

    // Compute initial matrix
    var centers = init_centers.toArray
    var new_matrix: RDD[(Vector, Vector)] = points.map{x => computeMatrix(x, centers)}
                                                  .cache()
    var old_matrix: RDD[(Vector, Vector)] = null
    var iter: Int = 0

    var init_loss = new_matrix.map {case (x, r) =>
      {
        var temp = 0.0
        for (j <- 0 to r.size - 1)
          temp = temp + pow(r(j), m) * Vectors.sqdist(x, centers(j))
        temp
      }
    }.sum()

    do {
      old_matrix = new_matrix

      // Update centers
      for (j <- 0 to numCluster - 1) {
        var temp = old_matrix.map{
          case (x, r) => (multiply(x, pow(r(j), m)), pow(r(j), m))
        }
        .reduce{(x, y) => (add(x._1, y._1), x._2 + y._2)}

        centers(j) = multiply(temp._1, 1.0 / temp._2)
      }

      // Update membership matrix
      new_matrix = old_matrix.map{case (x, r) => computeMatrix(x, centers)}
                             .cache()
      iter = iter + 1

      println("\n-------> ITERACIÓN " + iter)

    } while (!stopping_cond(old_matrix, new_matrix) && iter < max_iter)

    var loss = new_matrix.map {case (x, r) =>
      {
        var temp = 0.0
        for (j <- 0 to r.size - 1)
          temp = temp + pow(r(j), m) * Vectors.sqdist(x, centers(j))
        temp
      }
    }.sum()

    (centers.toList, new_matrix, init_loss, loss, iter)
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
    val points = input.map(line => Vectors.dense(line.split(',').map(_.toDouble)))
                 .repartition(4)
                 .cache()

    println("\n--------------> LEÍDOS " + points.count + " PUNTOS\n")

    // Compute centroids
    var centers = List[Vector]()
    if (local) {
      centers = points.mapPartitions(localChiu).collect().toList
    }
    else {
      centers = globalChiu(points)
    }

    val fcm = fuzzyCMeans(points, centers)

    println("\n--------------> NÚMERO DE CENTROS ENCONTRADOS: " + centers.length)
    println("--------------> FUNCIÓN DE PÉRDIDA: " + fcm._3)
    println("--------------> CENTROS ENCONTRADOS:\n" + resultToString(centers))

    println("\n--------------> NÚMERO DE CENTROS ACTUALIZADOS: " + fcm._1.length)
    println("--------------> FUNCIÓN DE PÉRDIDA: " + fcm._4)
    println("--------------> ITERACIONES: " + fcm._5)
    println("--------------> CENTROS ACTUALIZADOS:\n" + resultToString(fcm._1))

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
