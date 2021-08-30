package kittoku.mvc.unit.property

import kittoku.mvc.debug.assertAlways
import java.nio.ByteBuffer


internal open class SoftEtherAsciiProperty : SoftEtherProperty() {
    override val valueType = SEP_ASCII_TYPE
    override val valueNum = 1
    override val length: Int
        get() = headerLength + Int.SIZE_BYTES + _value.size

    private var _value = ByteArray(0)
    internal var value = ""
        get() = _value.toString(Charsets.US_ASCII)
        set(value) {
            _value = value.toByteArray(Charsets.US_ASCII)
            field = value
        }

    override fun write(buffer: ByteBuffer) {
        writeHeader(buffer)
        buffer.putInt(_value.size)
        buffer.put(_value)
    }

    override fun read(buffer: ByteBuffer) {
        assertAlways(buffer.int == valueType)
        assertAlways(buffer.int == valueNum)

        val size = buffer.int
        assertAlways(size >= 0)

        val dst = ByteArray(size)
        buffer.get(dst)

        _value = dst

        value // Check if it can be ASCII-decoded
    }
}

internal class SepMethod : SoftEtherAsciiProperty() {
    override val key = SEP_METHOD
}

internal class SepUsername : SoftEtherAsciiProperty() {
    override val key = SEP_USERNAME
}

internal class SepHubName : SoftEtherAsciiProperty() {
    override val key = SEP_HUB_NAME
}

internal class SepClientProductName : SoftEtherAsciiProperty() {
    override val key = SEP_CLIENT_PRODUCT_NAME
}
