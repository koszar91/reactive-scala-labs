package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props, Scheduler}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log = Logging(context.system, this)

  val cartTimerDuration: FiniteDuration = 5 seconds
  private def scheduleTimer: Cancellable =
    context.system.scheduler.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item: Any) =>
      context become nonEmpty(Cart(Seq(item)), scheduleTimer)

    case _ =>
      log.debug(s"Received unknown message while in empty state")
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item: Any) =>
      timer.cancel()
      context become nonEmpty(cart.addItem(item), scheduleTimer)

    case RemoveItem(item: Any) =>
      val newCart = cart.removeItem(item)
      if (newCart.size == 0) context become empty

    case ExpireCart =>
      context become empty

    case StartCheckout =>
      context become inCheckout(cart)

    case _ =>
      log.debug(s"Received unknown message while in non-empty state")
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      context become nonEmpty(cart, scheduleTimer)

    case ConfirmCheckoutClosed =>
      context become empty

    case _ =>
      log.debug(s"Received unknown message while in in-checkout state")
  }

}
