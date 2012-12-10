package eractor

import akka.util.Timeout

sealed trait EractorState
case class Ready(handler:Any => EractorState, timeout:Timeout) extends EractorState
case object Finished extends EractorState