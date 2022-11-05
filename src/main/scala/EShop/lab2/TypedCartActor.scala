package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                             extends Command
  case class RemoveItem(item: Any)                                          extends Command
  case object ExpireCart                                                    extends Command
  case class StartCheckout(orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ConfirmCheckoutCancelled                                      extends Command
  case object ConfirmCheckoutClosed                                         extends Command
  case class GetItems(sender: ActorRef[Cart])                               extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event

  def apply(): Behavior[TypedCartActor.Command] = Behaviors.setup(
    _ => {
      val actor = new TypedCartActor()
      actor.start
    }
  )
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) => {
      msg match {
        case AddItem(item: Any) =>
          nonEmpty(Cart(Seq(item)), scheduleTimer(context))
      }
    }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) => {
      msg match {
        case RemoveItem(item: Any) =>
          if (cart.removeItem(item).size == 0) {
            timer.cancel()
            empty
          }
          else {
            Behaviors.same
          }

        case ExpireCart => empty

        case StartCheckout(orderManagerRef: ActorRef[OrderManager.Command]) =>
          timer.cancel()
          val checkoutActor = context.spawn(TypedCheckout(context.self), "checkout-actor")
          orderManagerRef ! OrderManager.ConfirmCheckoutStarted(checkoutActor)
          inCheckout(cart)
      }
    }
  )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) => {
      msg match {
        case ConfirmCheckoutCancelled =>
          nonEmpty(cart, scheduleTimer(context))
        case ConfirmCheckoutClosed =>
          empty
      }
    }
  )

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.system.scheduler.scheduleOnce(
      cartTimerDuration,
      () => {
        context.self ! ExpireCart
      }
    )(context.executionContext)
}
