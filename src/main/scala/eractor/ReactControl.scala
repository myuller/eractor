package eractor

/**
 * Wrapper for ''react'' partial function result allowing
 * to introduce basic flow control inside pattern matching
 */
sealed trait ReactControl[+T]
case class Return[T](payload:T) extends ReactControl[T]
case class Escape(location: () => Any @eractorUnit) extends ReactControl[Nothing]