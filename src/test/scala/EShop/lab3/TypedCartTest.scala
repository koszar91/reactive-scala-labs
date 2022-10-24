package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import EShop.lab3.TypedCartTest._
import akka.actor.Cancellable
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  it should "add item properly" in {
    val probe = testKit.createTestProbe[Any]()
    val cart = cartActorWithCartSizeResponseOnStateChange(testKit, probe.ref)
    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)

    cart ! AddItem("CORMEN")
    probe.expectMessage(nonEmptyMsg)
    probe.expectMessage(1)
  }

  it should "be empty after adding and removing the same item" in {
    val probe = testKit.createTestProbe[Any]()
    val cartActor = cartActorWithCartSizeResponseOnStateChange(testKit, probe.ref)
    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)

    cartActor ! AddItem("CORMEN")
    probe.expectMessage(nonEmptyMsg)
    probe.expectMessage(1)

    cartActor ! RemoveItem("CORMEN 2")
    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)
  }

  it should "start checkout" in {
    val probe = testKit.createTestProbe[Any]()
    val cart = cartActorWithCartSizeResponseOnStateChange(testKit, probe.ref)

    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)

    cart ! AddItem("CORMEN")
    probe.expectMessage(nonEmptyMsg)
    probe.expectMessage(1)

    cart ! TypedCartActor.StartCheckout(testKit.createTestProbe[OrderManager.Command]().ref)
    probe.expectMessage(inCheckoutMsg)
    probe.expectMessage(1)
  }
}

object TypedCartTest {
  val emptyMsg      = "empty"
  val nonEmptyMsg   = "nonEmpty"
  val inCheckoutMsg = "inCheckout"

  def cartActorWithCartSizeResponseOnStateChange(testKit: ActorTestKit,
                                                 probe: ActorRef[Any]): ActorRef[TypedCartActor.Command] =
    testKit.spawn {
      val cartActor = new TypedCartActor {
        override val cartTimerDuration: FiniteDuration = 1.seconds

        override def empty: Behavior[TypedCartActor.Command] =
          Behaviors.setup(_ => {
            probe ! emptyMsg
            probe ! 0
            super.empty
          })

        override def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] =
          Behaviors.setup(_ => {
            probe ! nonEmptyMsg
            probe ! cart.size
            super.nonEmpty(cart, timer)
          })

        override def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] =
          Behaviors.setup(_ => {
            probe ! inCheckoutMsg
            probe ! cart.size
            super.inCheckout(cart)
          })

      }
      cartActor.start
    }

}
