/** FuzzyCMeans.scala
 *  Copyright (C) 2020 antcc
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see
 *  https://www.gnu.org/licenses/gpl-3.0.html.
 */

package fuzzyspark.clustering

import org.apache.spark.rdd.RDD

/** FuzzyCMeans
 *
 */
class FuzzyCMeans {
  private val 
  def x(data: RDD[Double]) = data.collect().foreach(println)
  def y() = println("Esto es una prueba.")
}
