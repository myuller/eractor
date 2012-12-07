package eractor

import java.util.UUID

/**
 * Globally unique values (with same purpose as Refs in Erlang)
 */
class Ref private(private val localId:Long, private val globalId:UUID) extends Serializable {

	override def equals(obj: Any) =
		if (obj == null || !obj.isInstanceOf[Ref])
			false
		else {
			val other = obj.asInstanceOf[Ref]
			other.localId == localId && other.globalId == globalId
		}

	override def hashCode() = {
		localId.hashCode() + 31 * globalId.hashCode()
	}

	override def toString = "Ref(%d, %s)" format (localId, globalId)
}

object Ref {
	private val machineId = UUID.randomUUID()
	private val counter = new java.util.concurrent.atomic.AtomicLong(0)
	def apply() = new Ref(counter.incrementAndGet(), machineId)
}
