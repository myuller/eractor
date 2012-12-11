package eractor.test

import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import org.scalatest.matchers.ShouldMatchers
import akka.actor.ActorSystem
import akka.util.Timeout
import akka.util.duration._

trait AkkaTest extends FlatSpec with ShouldMatchers with BeforeAndAfterAll {

	protected implicit var system: ActorSystem = null
	protected implicit val timeout = Timeout(5.seconds)

	override protected def beforeAll() {
		system = ActorSystem("test")
	}

	override protected def afterAll() {
		system.shutdown()
	}
}
