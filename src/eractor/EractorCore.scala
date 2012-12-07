package eractor

import scala.util.continuations._
import collection.mutable
import akka.util.Timeout

trait EractorCore {

	def body : Any @eractorUnit

	private val queue = mutable.Buffer.empty[Any]
	protected val queueMaxSize = 4096

	private var state:EractorState = Finished

	def start:EractorState = {
		state = reset[EractorState, EractorState]{
			body
			Finished
		}

		state
	}

	def feed(message:Any):EractorState = {
		state match {
			case Ready(handler, _) =>
				state = handler(message)
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
			val pendingIndex = queue.indexWhere(extractor.isDefinedAt _)
			if (pendingIndex >= 0){
				val message = queue.remove(pendingIndex)
				k(extractor(message))
			} else {
				// construct and return future message handling function with current extractor if no matching message in queue found
				def handler(message:Any):EractorState = {
					if (extractor.isDefinedAt(message)) // execute body with received message
						k(extractor(message))
					else { // put message in queue and return same handler again
						if (queue.length >= queueMaxSize)
							sys error "queue size exceeded"
						queue.prepend(message)
						Ready(handler _, timeout)
					}
				}

				Ready(handler _, timeout)
			}
		}
	}

	def shiftUnit: Unit @eractorUnit = shift[Unit, EractorState, EractorState]{ k:( Unit => EractorState ) =>
		k(())
	}
}

