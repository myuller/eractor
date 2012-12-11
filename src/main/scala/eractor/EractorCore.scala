package eractor

import scala.util.continuations._
import collection.mutable
import akka.util.Timeout
import akka.actor.{Actor, ActorRef}

trait EractorCore {

	def loop : Any @eractorUnit

	private val queue = mutable.Buffer.empty[(ActorRef, Any)]
	protected val queueMaxSize = 0

	private var state:EractorState = Finished

	def start:EractorState = {
		state = reset[EractorState, EractorState]{
			loop
			Finished
		}

		state
	}

	private var capturedSender:Option[ActorRef] = None

	protected def realSender = capturedSender.get

	def feed(message:Any, sender:ActorRef):EractorState = {
		state match {
			case Ready(handler, _) =>
				state = handler(message, sender)
				state
			case Finished =>
				sys.error("finished")
		}
	}

	protected def react[T](extractor:PartialFunction[Any,T]): T @cpsParam[EractorState, EractorState] =
		react(Timeout.never, extractor)

	protected def react[T](timeout:Timeout, extractor:PartialFunction[Any, T]):T @cpsParam[EractorState, EractorState] = {
		shift[T, EractorState, EractorState] { k:(T => EractorState) =>
			// check queue for pending messages:
			val pendingIndex = queue.indexWhere{ case(_, msg) => extractor.isDefinedAt(msg) }
			if (pendingIndex >= 0){
				val (captSender, message) = queue.remove(pendingIndex)
				captureSender(captSender){
					k(extractor(message))
				}
			} else {
				// construct and return future message handling function with current extractor if no matching message in queue found
				def handler(message:Any, sender:ActorRef):EractorState = {
					if (extractor.isDefinedAt(message)) // execute loop with received message
						captureSender(sender){
							k(extractor(message))
						}
					else { // put message in queue and return same handler again
						if (queueMaxSize > 0 && queue.length >= queueMaxSize)
							sys error "queue size exceeded"
						queue.prepend((sender, message))
						Ready(handler _, timeout)
					}
				}

				Ready(handler _, timeout)
			}
		}
	}

	private def captureSender[T](sender:ActorRef)(action: => T):T = {
		capturedSender = Some(sender)
		try {
			action
		} finally {
			capturedSender = None
		}
	}

	def shiftUnit: Unit @eractorUnit = shift[Unit, EractorState, EractorState]{ k:( Unit => EractorState ) =>
		k(())
	}
}

