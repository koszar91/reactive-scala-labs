package EShop.lab3

import EShop.lab2.TypedCartActor._
import EShop.lab2.TypedCheckout
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import TypedCheckout._

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val cartProbe = testKit.createTestProbe[Any]()
    val orderManagerProbe = testKit.createTestProbe[Any]()
    val checkoutActor = testKit.spawn(TypedCheckout(cartProbe.ref), "checkout-actor")

    checkoutActor ! SelectDeliveryMethod("Kurier")
    checkoutActor ! SelectPayment("Gotówa za pobraniem", orderManagerProbe.ref)
    orderManagerProbe.expectMessageType[OrderManager.ConfirmPaymentStarted]

    checkoutActor ! TypedCheckout.ConfirmPaymentReceived
    cartProbe.expectMessage(ConfirmCheckoutClosed)
  }

}
