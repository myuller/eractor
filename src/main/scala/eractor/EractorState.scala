package eractor

import akka.util.Timeout
import akka.actor.ActorRef

sealed trait EractorState
case class Ready(handler:(Any,ActorRef) => EractorState, timeout:Timeout) extends EractorState
case object Finished extends EractorState