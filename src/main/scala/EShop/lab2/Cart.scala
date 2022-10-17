package EShop.lab2

import scala.collection.mutable.ListBuffer

case class Cart(items: Seq[Any]) {
  def contains(item: Any): Boolean = items.contains(item)
  def addItem(item: Any): Cart     = Cart(items :+ item)
  def removeItem(item: Any): Cart  = Cart(items.filter(_ != item))
  def size: Int                    = items.size
}

object Cart {
  def empty: Cart = Cart(Seq())
}
