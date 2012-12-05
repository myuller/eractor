package eractor.test

import org.scalatest.matchers.{MatchResult, Matcher, ShouldMatchers}
import org.scalatest.FlatSpec
import eractor._
import akka.util.duration._
import akka.util.Timeout

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
				receive{ case _ => 0 }
				()
			}
		}.start should beReady
	}

	it should "receive and process messages" in {
		val core = new EractorCore {
			var status = 1
			def body() = {
				val q = receive {
					case x:Int => status = x
						status + 19
				}
				receive {
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
				receive {
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
				receive {
					case 99 =>
						state ::=  99
				}
				receive {
					case 128 =>
						state ::= 128
				}
				receive {
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
				receive(5.seconds, {
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

	it should "recurse" in {
		val core = new EractorCore{
			var state = ""
			def body() = {
				jump(body())
//				receive[Unit]{
//					case x:String if state.length < 10 =>
//						state += x
//						jump(body())
//					case _ =>
//						state += "!"
//				}
			}
		}

		core.start should beReady
		for (i <- 1 to 10){
			core.feed(i.toString) should beReady
		}

		core.state should be("123456789!")
	}

	private val beReady = Matcher{ (state:EractorState) =>
		MatchResult(state != Finished, "was finished", "was not finished")
	}

	private def eractor(bBody: => Unit ) = new EractorCore{
		def body() = { bBody }
	}
}
