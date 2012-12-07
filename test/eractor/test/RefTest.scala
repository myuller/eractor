package eractor.test

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FlatSpec
import eractor.Ref
import java.io.{ObjectInputStream, ByteArrayInputStream, ObjectOutputStream, ByteArrayOutputStream}

/**
 *
 */
class RefTest extends FlatSpec with ShouldMatchers {
	behavior of "Ref"

	it should "display usable information in 'toString' method" in {
		Ref().toString should fullyMatch regex("""^Ref\(\d+,\s*[\da-f]+(-[\da-f]+)+\)$""")
	}

	it should "produce unique reference on every call" in {
		Ref() should not be(Ref())
	}

	it should "match references in patterns" in {
		val ref = Ref()
		val ref2 = Ref()

		(Some(ref) match {
			case Some(`ref`) =>
				true
			case _ => false
		}) should be(true)

		(Some(ref2) match {
			case Some(`ref`) =>
				1
			case Some(`ref2`) =>
				2
		}) should be(2)
	}

	it should "serialize and deserialize references properly" in {
		val stream = new ByteArrayOutputStream()
		val oos = new ObjectOutputStream(stream)
		val ref = Ref()
		val ref2 = Ref()
		oos.writeObject(ref)
		stream.flush()
		val inStream = new ByteArrayInputStream(stream.toByteArray)
		val ois = new ObjectInputStream(inStream)
		val deserialized = (ois.readObject().asInstanceOf[Ref])
		deserialized should be(ref)
		deserialized should not be(ref2)
	}
}
