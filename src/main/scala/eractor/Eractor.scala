package eractor

import akka.actor.Actor
import akka.util.Timeout
import annotation.tailrec

trait Eractor extends EractorCore { this: Actor =>

	private var timeoutHandle:Option[Ref] = None

	override def preStart() {
		operateCore(start)
	}

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