package EShop.lab4

import EShop.lab3.OrderManager
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.SerializationSettings

import scala.concurrent.duration._
import scala.util.Random

class PersistentCartActorTest
  extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  override def afterAll: Unit = testKit.shutdownTestKit()

  import EShop.lab2.TypedCartActor._

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[Command, Event, State](
      system,
      new PersistentCartActor {
        override val cartTimerDuration: FiniteDuration = 1.second
      }.apply(generatePersistenceId),
      SerializationSettings.disabled
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  def generatePersistenceId: PersistenceId = PersistenceId.ofUniqueId(Random.alphanumeric.take(256).mkString)

  it should "change state after adding first item to the cart" in {
    val result = eventSourcedTestKit.runCommand(AddItem("Hamlet"))

    result.event.isInstanceOf[ItemAdded] shouldBe true
    result.state.isInstanceOf[NonEmpty] shouldBe true
  }

  it should "be empty after adding new item and removing it after that" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Storm"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    val resultRemove = eventSourcedTestKit.runCommand(RemoveItem("Storm"))

    resultRemove.event shouldBe CartEmptied
    resultRemove.state shouldBe Empty
  }

  it should "contain one item after adding new item and removing not existing one" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Romeo & Juliet"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    val resultRemove = eventSourcedTestKit.runCommand(RemoveItem("Macbeth"))

    resultRemove.hasNoEvents shouldBe true
    resultRemove.state.isInstanceOf[NonEmpty] shouldBe true
  }

  it should "change state to inCheckout from nonEmpty" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Romeo & Juliet"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    val resultStartCheckout =
      eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[OrderManager.Command]().ref))

    resultStartCheckout.event.isInstanceOf[CheckoutStarted] shouldBe true
    resultStartCheckout.state.isInstanceOf[InCheckout] shouldBe true
  }

  it should "cancel checkout properly" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Cymbelin"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    val resultStartCheckout =
      eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[OrderManager.Command]().ref))

    resultStartCheckout.event.isInstanceOf[CheckoutStarted] shouldBe true
    resultStartCheckout.state.isInstanceOf[InCheckout] shouldBe true

    val resultCancelCheckout =
      eventSourcedTestKit.runCommand(ConfirmCheckoutCancelled)

    resultCancelCheckout.event shouldBe CheckoutCancelled
    resultCancelCheckout.state.isInstanceOf[NonEmpty] shouldBe true
  }

  it should "close checkout properly" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Cymbelin"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    val resultStartCheckout =
      eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[OrderManager.Command]().ref))

    resultStartCheckout.event.isInstanceOf[CheckoutStarted] shouldBe true
    resultStartCheckout.state.isInstanceOf[InCheckout] shouldBe true

    val resultCloseCheckout =
      eventSourcedTestKit.runCommand(ConfirmCheckoutClosed)

    resultCloseCheckout.event shouldBe CheckoutClosed
    resultCloseCheckout.state shouldBe Empty
  }

  it should "not add items when in checkout" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Cymbelin"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    val resultStartCheckout =
      eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[OrderManager.Command]().ref))

    resultStartCheckout.event.isInstanceOf[CheckoutStarted] shouldBe true
    resultStartCheckout.state.isInstanceOf[InCheckout] shouldBe true

    val resultAdd2 = eventSourcedTestKit.runCommand(AddItem("Henryk V"))

    resultAdd2.hasNoEvents shouldBe true
    resultAdd2.state.isInstanceOf[InCheckout] shouldBe true
  }

  it should "not change state to inCheckout from empty" in {
    val resultStartCheckout =
      eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[OrderManager.Command]().ref))

    resultStartCheckout.hasNoEvents shouldBe true
    resultStartCheckout.state shouldBe Empty
  }

  it should "expire and back to empty state after given time" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("King Lear"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    Thread.sleep(1500)

    val resultAdd2 = eventSourcedTestKit.runCommand(RemoveItem("King Lear"))

    resultAdd2.hasNoEvents shouldBe true
    resultAdd2.state shouldBe Empty
  }

  // RESTORING ACTOR TESTS

  it should "contain added items after restart" in {
    val items = List("A", "B", "C")
    items.foreach(item => eventSourcedTestKit.runCommand(AddItem(item)))

    val restartResult = eventSourcedTestKit.restart()
    items.foreach(item => restartResult.state.cart.contains(item) shouldBe true)
  }

  it should "stay in checkout with timer not cancelled after restart" in {
    eventSourcedTestKit.runCommand(AddItem("A"))
    eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[OrderManager.Command].ref))

    val restartResult = eventSourcedTestKit.restart()
    restartResult.state.isInstanceOf[InCheckout] shouldBe true
  }

  it should "not add items when in checkout after restart" in {
    eventSourcedTestKit.runCommand(AddItem("A"))
    eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[Any]().ref))

    eventSourcedTestKit.restart()

    val restartResult = eventSourcedTestKit.runCommand(AddItem("B"))
    restartResult.state.cart.size shouldBe 1
  }

  it should "be empty after cart expires despite restarts" in {
    val item = "A"
    val addItemResult = eventSourcedTestKit.runCommand(AddItem(item))
    addItemResult.state.cart.contains(item) shouldBe true
    addItemResult.state.isInstanceOf[NonEmpty] shouldBe true

    Thread.sleep(2000)
    val restartResult = eventSourcedTestKit.restart()
    restartResult.state.cart.contains(item) shouldBe false
    restartResult.state shouldBe Empty
  }
}
