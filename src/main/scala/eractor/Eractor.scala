package eractor

import akka.actor.Actor
import akka.util.Timeout
import annotation.tailrec

/**
 * Incorporates EractorCore into Akka's Actor trait providing
 * implementations for "receive" and "preStart" methods
 */
trait Eractor extends EractorCore { this: Actor =>

	private var timeoutHandle:Option[Ref] = None

	override def preStart() {
		operateCore(start) // run "body" function up to first "react" call
	}

	// Receive implementation handles two types of messages:
	// * timeouts -- the (Timeout, Ref) tuple sent by ''operateCore'' method to ''self'' to implement
	// reaction timeouts
	// * any other messages are passed without modification to core.
	protected def receive:Receive = {
		case (Timeout, ref:Ref) =>
			// if timeoutHandle contains right reference we send Timeout message
			// otherwise we ignore value
			if (timeoutHandle.exists(_ == ref)) {
				operateCore(feed(Timeout, sender))
			}
		case message =>
			operateCore(feed(message, sender))
	}

	/**
	 * Examines current state of the core and
	 * performs operations to support timeouts and
	 * actor stopping if required
	 * @param state last EractorState reported by EractorCore
	 */
	@tailrec
	private def operateCore(state:EractorState) {
		state match {
			case Finished =>
				context stop context.self
			case Ready(_, timeout) =>
				timeout match {
					case Timeout.never =>
						timeoutHandle = None
					case Timeout.zero =>
						operateCore(feed(Timeout, sender))
					case Timeout(duration) =>
						val ref = Ref()
						context.system.scheduler.scheduleOnce(duration, self, (Timeout, ref))
						timeoutHandle = Some(ref)
				}
		}
	}
}
