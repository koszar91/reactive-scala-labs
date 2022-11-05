package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

}

class Checkout extends Actor {

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration = 1 seconds

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      context become selectingDelivery(scheduleTimer(checkoutTimerDuration))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout =>
      context become cancelled

    case SelectDeliveryMethod(method: String) =>
      context become selectingPaymentMethod(timer)
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout =>
      context become cancelled

    case SelectPayment(payment: String) =>
      timer.cancel()
      context become processingPayment(scheduleTimer(paymentTimerDuration))
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout =>
      context become cancelled

    case ConfirmPaymentReceived =>
      context become closed
  }

  def cancelled: Receive = {
    context stop self
    LoggingReceive {
      case _ => None
    }
  }

  def closed: Receive = {
    context stop self
    LoggingReceive {
      case _ => None
    }
  }

  private def scheduleTimer(duration: FiniteDuration): Cancellable =
    scheduler.scheduleOnce(duration, self, CancelCheckout)
}
