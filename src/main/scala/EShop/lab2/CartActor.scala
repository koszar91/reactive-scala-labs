package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props, Scheduler}
import akka.event.{Logging, LoggingReceive}
import CartActor._
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

  def props: Props = Props(new CartActor())
}

class CartActor extends Actor {

  val cartTimerDuration: FiniteDuration = 5 seconds

  private val scheduler = context.system.scheduler
  private val log = Logging(context.system, this)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item: Any) =>
      context become nonEmpty(Cart(Seq(item)), scheduleTimer)
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item: Any) =>
      timer cancel;
      context become nonEmpty(cart.addItem(item), scheduleTimer)

    case RemoveItem(item: Any) =>
      if (cart.removeItem(item).size == 0)
        context become empty

    case ExpireCart =>
      context become empty

    case StartCheckout =>
      context become inCheckout(cart)
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      context become nonEmpty(cart, scheduleTimer)

    case ConfirmCheckoutClosed =>
      context become empty
  }

  private def scheduleTimer: Cancellable =
    scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)
}
