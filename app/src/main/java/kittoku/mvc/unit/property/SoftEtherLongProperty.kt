package kittoku.mvc.unit.property

import kittoku.mvc.debug.assertAlways
import java.nio.ByteBuffer


internal open class SoftEtherLongProperty : SoftEtherProperty() {
    override val valueType = SEP_LONG_TYPE
    override val valueNum = 1
    override val length: Int
        get() = headerLength +Long.SIZE_BYTES

    internal var value = 0L

    override fun write(buffer: ByteBuffer) {
        writeHeader(buffer)
        buffer.putLong(value)
    }

    override fun read(buffer: ByteBuffer) {
        assertAlways(buffer.int == valueType)
        assertAlways(buffer.int == valueNum)

        value = buffer.long
    }
}
