package kittoku.mvc.unit.property

import kittoku.mvc.debug.assertAlways
import java.nio.ByteBuffer


internal open class SoftEther160bitsProperty : SoftEtherProperty() {
    override val valueType = SEP_BYTES_TYPE
    override val valueNum = 1

    override val length: Int
        get() = headerLength + Int.SIZE_BYTES + value.size

    internal val value = ByteArray(20)

    override fun write(buffer: ByteBuffer) {
        writeHeader(buffer)
        buffer.putInt(value.size)
        buffer.put(value)
    }

    override fun read(buffer: ByteBuffer) {
        assertAlways(buffer.int == valueType)
        assertAlways(buffer.int == valueNum)
        assertAlways(buffer.int == value.size)

        buffer.get(value)
    }
}

internal class SepSecurePassword : SoftEther160bitsProperty() {
    override val key = SEP_SECURE_PASSWORD
}

internal class SepRandom : SoftEther160bitsProperty() {
    override val key = SEP_RANDOM
}
