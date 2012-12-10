package eractor.test

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import akka.testkit.TestActorRef
import akka.pattern._
import akka.util.duration._
import eractor.Eractor
import akka.actor.{ActorSystem, Actor}
import akka.dispatch.Await
import akka.util.Timeout
import akka.util

class EractorTest extends FlatSpec with ShouldMatchers with BeforeAndAfterAll {
	behavior of "Eractor"

	implicit var system:ActorSystem = null
	implicit val timeout = util.Timeout(5.seconds)

	it should "terminate eractor without 'react'-s immediately" in {

		var state = "no!"

		val ref = TestActorRef(new Actor with Eractor{

			def loop = {
				state = "ok!"
			}
		})

		ref.isTerminated should be(true)
		state should be("ok!")
	}

	it should "start eractor and wait for messages" in {
		val ref = TestActorRef(new Actor with Eractor {
			var state = 0
			def loop = {

				react{
					case msg:Int =>
						state += msg
						sender ! state
				}

				loop
			}
		})

		ref.isTerminated should be(false)

		val result = Await.result((ref ? 1).mapTo[Int], timeout.duration)
		result should be(1)
		val result2 = Await.result((ref ? 2).mapTo[Int], timeout.duration)
		result2 should be(3)
	}

	it should "introduce timeouts" in {
		val ref = TestActorRef(new Actor with Eractor{
			def loop = {
				var state = "no!"

				// wait for anything after start and if there's nothing change state
				react(500.milliseconds, {
					case Timeout =>
						state = "ok!"
					case _ =>
						state = "nono!"
				})

				// wait for state request
				react{
					case _ => sender ! state
				}
			}
		})

		Thread.sleep(600)

		Await.result((ref ? ()).mapTo[String], timeout.duration) should be("ok!")
	}

	it should "handle zero timeouts" in {
		val ref = TestActorRef(new Actor with Eractor{
			var state = "no!"
			def loop = {
				react(Timeout.zero, {
					case Timeout =>
						state = "ok!"
					case _ =>
						state = "nono!"
				})
			}
		})

		ref.isTerminated should be(true)
	}

	override protected def beforeAll() {
		system = ActorSystem("test")
	}

	override protected def afterAll() {
		system.shutdown()
	}
}