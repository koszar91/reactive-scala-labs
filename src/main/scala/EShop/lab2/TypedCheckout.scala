package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event

  def apply(cartActor: ActorRef[TypedCartActor.Command]): Behavior[TypedCheckout.Command] = Behaviors.setup(
    _ => {
      val actor = new TypedCheckout(cartActor)
      actor start
    }
  )
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.setup(
    context => selectingDelivery(scheduleTimer(context, checkoutTimerDuration, ExpireCheckout))
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case SelectDeliveryMethod(method: String) =>
      selectingPaymentMethod(timer)

    case CancelCheckout =>
      cancelled

    case ExpireCheckout =>
      cancelled
  }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) => {
      msg match {
        case SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) =>
          timer.cancel()
          val paymentActor = context.spawn(Payment(payment, orderManagerRef, context.self), "payment-actor")
          orderManagerRef ! OrderManager.ConfirmPaymentStarted(paymentActor)
          processingPayment(scheduleTimer(context, paymentTimerDuration, ExpirePayment))

        case CancelCheckout =>
          cancelled

        case ExpireCheckout =>
          cancelled
      }
    }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case ConfirmPaymentReceived =>
      timer.cancel()
      cartActor ! TypedCartActor.ConfirmCheckoutClosed
      closed

    case CancelCheckout =>
      cancelled

    case ExpirePayment =>
      cancelled
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] = Behaviors.stopped

  private def scheduleTimer(context: ActorContext[TypedCheckout.Command],
                            duration: FiniteDuration, command: Command): Cancellable =
    context.system.scheduler.scheduleOnce(
      duration,
      () => { context.self ! command }
    )(context.executionContext)
}
