package eractor

import akka.util.Timeout
import akka.actor.ActorRef

/**
 * Current state of EractorCore
 */
sealed trait EractorState

/**
 * EractorCore is ready to accept messages.
 * @param handler message handler function
 * @param timeout timeout, as declared in ''react'' call
 */
case class Ready(handler:(Any,ActorRef) => EractorState, timeout:Timeout) extends EractorState

/**
 * EractorCore has finished execution
 */
case object Finished extends EractorState