package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  case object DoPayment extends Command

  sealed trait Event
  case object PaymentConfirmed extends Event

  def apply(method: String,
            orderManager: ActorRef[OrderManager.Command],
            checkout: ActorRef[TypedCheckout.Command]): Behavior[Payment.Command] =
    Behaviors.setup(
      _ => {
        val actor = new Payment(method, orderManager, checkout)
        actor.start
      }
    )
}

class Payment(method: String,
              orderManager: ActorRef[OrderManager.Command],
              checkout: ActorRef[TypedCheckout.Command]) {

  import Payment._

  def start: Behavior[Payment.Command] = Behaviors.receiveMessage {
    case DoPayment =>
      checkout ! TypedCheckout.ConfirmPaymentReceived
      orderManager ! OrderManager.ConfirmPaymentReceived
      Behaviors.stopped
  }
}
