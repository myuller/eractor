package eractor.test

import org.scalatest.matchers.{MatchResult, Matcher, ShouldMatchers}
import org.scalatest.FlatSpec
import eractor._
import akka.util.duration._
import akka.util.Timeout
import util.continuations.cpsParam

class EractorCoreTest extends FlatSpec with ShouldMatchers {
	behavior of "EractorCore"

	it should "be finished when no receiving is intended" in {
		eractor{ () }.start should not(beReady)
	}

	it should "initialize on start" in {
		var flag = false
		val e = eractor {
			flag = true
		}
		flag should be(right = false)
		e.start should not(beReady)
		flag should be(right = true)
	}

	it should "be ready to receive messages if body contains \"receive\" functions" in {
		new EractorCore {
			def body() = {
				react{ case _ => 0 }
				()
			}
		}.start should beReady
	}

	it should "receive and process messages" in {
		val core = new EractorCore {
			var status = 1
			def body() = {
				val q = react {
					case x:Int => status = x
						status + 19
				}
				react {
					case y:Int => status = y * q
				}
			}
		}

		core.start should beReady
		core.feed(5) should beReady
		core.status should be(5)
		core.feed(11) should not(beReady)
		core.status should be((5+19) * 11)
	}

	it should "queue unmatching messages" in {
		val core = new EractorCore {
			def body() = {
				react {
					case 99 =>
						()
				}
			}
		}

		core.start should beReady
		core.feed(11) should beReady
		core.feed(99) should not(beReady)
	}

	it should "unqueue messages previously unmatched after successfull match" in {
		var state = List.empty[Int]
		val core = new EractorCore {
			def body() = {
				react {
					case 99 =>
						state ::=  99
				}
				react {
					case 128 =>
						state ::= 128
				}
				react {
					case 99 =>
						state ::= 88
				}
			}
		}

		core.start should beReady
		core.feed(128) should beReady
		state should be(List.empty)
		core.feed(99) should beReady
		state should be(List(128,99))
		core.feed(99) should not(beReady)
		state should be(List(88,128,99))
	}

	it should "time out" in {
		var expired = false
		val core = new EractorCore{
			def body() = {
				react(5.seconds, {
					case Expired => expired = true
					case _ => expired = false
				})
			}
		}

		val state = core.start.asInstanceOf[Ready]
		state.timeout should be(Timeout(5.seconds))
		core.feed(Expired) should not(beReady)
		expired should be(right=true)
	}

	it should "recur" in {
		val core = new EractorCore{
			var state = ""
			def body() = {
				val finished = react{
					case x:String if state.length < 9 =>
						state += x
						false
					case _ =>
						state += "!"
						true
				}

				if (!finished)
					body()
				else
					shiftUnit
			}
		}

		core.start should beReady
		for (i <- 1 to 9){
			core.feed(i.toString) should beReady
		}

		core.feed("10") should not(beReady)

		core.state should be("123456789!")
	}

	it should "use constant stack space when recurring" in {
		val core = new EractorCore{
			var state = 0L

			def body = {

				state -= 1

				loop
			}

			def loop: Unit @eractorUnit = {
				react {
					case x:Int =>
						state += x
				}

				body
			}
		}

		core.start should beReady
		for(i <- 1 to 1000000)
			core.feed(i) should beReady

		core.state should be(499999499999L)
	}

	private val beReady = Matcher{ (state:EractorState) =>
		MatchResult(state != Finished, "was finished", "was not finished")
	}

	private def eractor(bBody: => Unit ) = new EractorCore{
		def body() = { bBody }
	}
}
