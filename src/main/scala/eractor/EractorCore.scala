package eractor

import scala.util.continuations._
import collection.mutable
import akka.util.Timeout
import akka.actor.{Actor, ActorRef}

/**
 * Contains state and behavior of Eractor
 */
trait EractorCore {

	/**
	 * Entry point to eractor behavior. This function usually contains all actor logic.
	 * Function gets called when actor is started and is executed until first
	 * ''react'' call is encountered or until the end of function is reached.
	 * @return Result is discarded
	 */
	def body : Any @eractorUnit

	/**
	* Keeps messages that couldn't be proceesed with current state of core
	*/
	private val queue = mutable.Buffer.empty[(ActorRef, Any)]

	/**
	* Maximum message queue size. If 0 then size is unbound.
	* When queue size reaches ''queueMaxSize'' the exception is
	* thrown. Which causes actor to crash.
	*/
	protected val queueMaxSize = 0

	/**
	* Current core state. Changes every time message is processed.
	*/
	private var state:EractorState = Finished

	/**
	* Initializes core. Returns core state after initialization
	*/
	def start:EractorState = {
		state = fireUp(body)
		state
	}

	/**
	 * Execute passed method inside delimited continuation and
	 * return execution state
	 * @param method method to execute
	 * @return state produced by method
	 */
	private def fireUp(method: => Any @eractorUnit): EractorState = {
		reset[EractorState, EractorState]{
			method
			Finished
		}
	}

	/**
	* Contains sender which was captured during message receive.
	* Is defined only when called after inside or after ''react'' call
	*/
	private var capturedSender:Option[ActorRef] = None

	/**
	* Has same meaning as ''sender'' in Akka actor, but behaves properly whenn ''react''ing to
	* queued messages.
	*/
	protected def realSender = capturedSender.get

	/**
	* Advances core to next state. This means one of things:
	* * if the message can be processed by current ''react'' function
	* the execution of ''body'' method resumes until next ''react'' call
	* or until end;
	* * if message cannot be used to resume body it is queued and same state returned;if 
	*/
	def feed(message:Any, sender:ActorRef):EractorState = {
		state match {
			case Ready(handler, _) =>
				state = handler(message, sender)
				state
			case Finished =>
				sys.error("finished")
		}
	}

	/**
	* Wait for message matching partial function passed as ''extractor'' indefinitely.
	*/
	protected def react[T](extractor:PartialFunction[Any,ReactControl[T]]): T @eractorUnit =
		react(Timeout.never, extractor)

	/**
	* Wait for message matching partial function passed as ''extractor'' for time specified in
	* ''timeout''.
	*/
	protected def react[T](timeout:Timeout, extractor:PartialFunction[Any, ReactControl[T]]):T @eractorUnit = {
		def extract(sender: ActorRef, k: (T) => EractorState, message: Any): EractorState = {
			captureSender(sender) {
				extractor(message) match {
					case Return(payload) => k(payload)
					case Escape(location) =>
						// pass the control flow to ''location'' function and never fire the continuation
						fireUp(location())
				}
			}
		}

		shift[T, EractorState, EractorState] { k:(T => EractorState) =>
			// check queue for pending messages:
			val pendingIndex = queue.indexWhere{ case(_, msg) => extractor.isDefinedAt(msg) }
			if (pendingIndex >= 0){
				val (captSender, message) = queue.remove(pendingIndex)
				extract(captSender, k, message)
			} else {
				// construct and return future message handling function with current extractor if no matching message in queue found
				def handler(message:Any, sender:ActorRef):EractorState = {
					if (extractor.isDefinedAt(message)) // execute body with received message
						extract(sender, k, message)
					else { // put message in queue and return same handler again
						if (message == Timeout)
							sys error "extractor must be defined on \"Timeout\""
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

	/**
	* Convenience function to help some control flow statements "type-check", for example:
	*
	* {{{
	*	var state = ""
	*	def body() = {
	*		val finished = react{
	*			case x:String if state.length < 9 =>
	*				state += x
	*				false
	*			case _ =>
	*				state += "!"
	*				true
	*		}
	*
	*		if (!finished)
	*			body()
	*		else
	*			shiftUnit // this would not compile if written as "()", but meaning is same
	*	}	
	* }}}
	*/
	def shiftUnit: Unit @eractorUnit = shift[Unit, EractorState, EractorState]{ k:( Unit => EractorState ) =>
		k(())
	}

	/**
	 * Wraps ordinary value returned by react into ReactControl instance
	 */
	protected implicit def reactReturn[T](value:T):ReactControl[T] = Return(value)

	/**
	 * Passes control to function ''location'' and never returns from continuation
	 * @param location Function to pass control to
	 */
	protected def escape(location: => Any @eractorUnit):ReactControl[Nothing] = Escape(() => location)
}

