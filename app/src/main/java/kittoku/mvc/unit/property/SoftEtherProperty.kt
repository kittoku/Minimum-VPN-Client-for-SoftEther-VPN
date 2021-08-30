package kittoku.mvc.unit.property

import kittoku.mvc.unit.DataUnit
import java.nio.ByteBuffer


internal abstract class SoftEtherProperty : DataUnit {
    internal open val key = ""
    protected abstract val valueType: Int
    protected abstract val valueNum: Int
    protected val headerLength: Int
        get() = key.length + 3 * Int.SIZE_BYTES // 12 = keySize + valueType + valueNum

    protected fun writeHeader(buffer: ByteBuffer) {
        buffer.putInt(key.length + 1)
        buffer.put(key.toByteArray(Charsets.US_ASCII))
        buffer.putInt(valueType)
        buffer.putInt(valueNum)
    }
}
