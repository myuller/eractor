package eractor.test

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import akka.testkit.{TestKit, TestActorRef}
import akka.pattern._
import akka.util.duration._
import eractor.{EractorCore, Eractor}
import akka.actor.{Props, ActorRef, ActorSystem, Actor}
import akka.dispatch.Await
import akka.util.Timeout
import akka.util

class EractorTest extends TestKit(ActorSystem("eractorTest")) with FlatSpec with ShouldMatchers {
	behavior of "Eractor"

	it should "terminate eractor without 'react'-s immediately" in {

		var state = "no!"

		val ref = TestActorRef(new Actor with Eractor {

			def body = {
				state = "ok!"
			}
		})

		ref.isTerminated should be(true)
		state should be("ok!")
	}

	it should "start eractor and wait for messages" in {
		val ref = TestActorRef(new Actor with Eractor {
			var state = 0

			def body = {

				react {
					case msg: Int =>
						state += msg
						sender ! state
				}

				body
			}
		})

		ref.isTerminated should be(false)

		val result = Await.result((ref ? 1).mapTo[Int], timeout.duration)
		result should be(1)
		val result2 = Await.result((ref ? 2).mapTo[Int], timeout.duration)
		result2 should be(3)
	}

	it should "introduce timeouts" in {
		val ref = TestActorRef(new Actor with Eractor {
			def body = {
				var state = "no!"

				// wait for anything after start and if there's nothing change state
				react(500.milliseconds, {
					case Timeout =>
						state = "ok!"
					case _ =>
						state = "nono!"
				})

				// wait for state request
				react {
					case _ => sender ! state
				}
			}
		})

		Thread.sleep(600)

		Await.result((ref ?()).mapTo[String], timeout.duration) should be("ok!")
	}

	it should "handle zero timeouts" in {
		val ref = TestActorRef(new Actor with Eractor {
			var state = "no!"

			def body = {
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

	it should "memorize and maintain sender for messages in queue" in {
		val dummy = Props(new Actor{ def receive = { case _ => () } })
		val sender1 = TestActorRef(dummy)
		val sender2 = TestActorRef(dummy)
		var capture1: ActorRef = null
		var capture2: ActorRef = null

		val ref = TestActorRef(new Actor with Eractor {

			def body = {
				react {
					case 1 =>
						capture1 = realSender
				}

				react {
					case 2 =>
						capture2 = realSender
				}
			}

		})

		ref.tell(2, sender2)
		ref.tell(1, sender1)

		capture1 should be(sender1)
		capture2 should be(sender2)
	}

	it should "perform complex interactions" in {

		case class Verb(value:String)

		var results = Set.empty[String]

		val helper = system.actorOf(Props(new Actor with Eractor{
			def body = {
				react{
					case "balloon" => realSender ! Verb("flies")
					case "pencil" => realSender ! Verb("draws")
					case "dog" => realSender ! Verb("barks")
				}

				body
			}
		}))

		val constructor = system.actorOf(Props(new Actor with Eractor{
			def body = {
				val noun = react{
					case str:String => str
				}

				helper ! noun

				val verb = react(Timeout(200.milliseconds), {
					case Verb(v) => v
					case Timeout => "who knows..."
				})

				results += noun + " " + verb

				body
			}
		}))

		system.actorOf(Props(new Actor with Eractor {
			def body = {
				constructor ! "balloon"
				constructor ! "banana"
				constructor ! "dog"
				constructor ! "pencil"
			}

			def sleep(timeout:Timeout) = {
				react(timeout, {case Timeout => () })
			}
		}))

		Thread.sleep(600)

		assert(results.contains("banana who knows..."))
		assert(results.contains("dog barks"))
		assert(results.contains("pencil draws"))
		assert(results.contains("balloon flies"))
	}


	it should "do ping pong example" in {
		class PingPong extends Actor with Eractor {
			var pings = 0
			var pongs = 0

			def body = {

				react {
					case 'ping =>
						pings += 1
						self ! 'pong
				}

				react {
					case 'pong =>
						pongs += 1
				}

				assert(pings == pongs)

				body // loop forever
			}
		}

		val pingPong = system.actorOf(Props(new PingPong))
		(1 to 1000).foreach(_ => pingPong ! 'ping)
	}

	protected implicit val timeout = Timeout(5.seconds)
}