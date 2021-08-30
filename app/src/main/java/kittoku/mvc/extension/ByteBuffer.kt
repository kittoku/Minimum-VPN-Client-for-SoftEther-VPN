package kittoku.mvc.extension

import java.nio.ByteBuffer


internal fun ByteBuffer.move(diff: Int) {
    position(position() + diff)
}

internal fun ByteBuffer.padZeroByte(size: Int) {
    repeat(size) { put(0) }
}

internal fun ByteBuffer.payload(): ByteArray {
    return array().sliceArray(position() until limit())
}


internal fun ByteBuffer.capacityAfterPayload(): Int {
    return capacity() - limit()
}
