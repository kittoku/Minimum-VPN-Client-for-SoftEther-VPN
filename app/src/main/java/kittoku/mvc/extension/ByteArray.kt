package kittoku.mvc.extension

import kittoku.mvc.unit.ip.IPv4_ADDRESS_SIZE
import java.net.InetAddress
import java.nio.ByteBuffer


internal fun ByteArray.isSame(other: ByteArray): Boolean {
    if (this.size != other.size) return false

    this.zip(other).forEach {
        if (it.first != it.second) return false
    }

    return true
}

internal fun ByteArray.copy(): ByteArray {
    return ByteArray(this.size).also {
        it.read(this)
    }
}

internal fun ByteArray.read(other: ByteArray) {
    val wrapped = ByteBuffer.wrap(other)
    wrapped.get(this)
}

internal fun ByteArray.toInetAddress(): InetAddress {
    return InetAddress.getByAddress(this)
}

internal fun ByteArray.toBroadcastAddress(subnetMask: ByteArray): ByteArray {
    if (this.size != IPv4_ADDRESS_SIZE || subnetMask.size != IPv4_ADDRESS_SIZE) {
        throw NotImplementedError()
    }

    val thisAsInt = ByteBuffer.wrap(this).int
    val maskAsInt = ByteBuffer.wrap(subnetMask).int
    val result = ByteBuffer.allocate(IPv4_ADDRESS_SIZE)

    result.putInt((thisAsInt and maskAsInt) or maskAsInt.inv())

    return result.array()
}

internal fun ByteArray.toHexString(parse: Boolean = false): String {
    var output = ""

    forEachIndexed { index, byte ->
        output += String.format("%02X", byte.toInt() and 0xFF)

        if (parse) output += if (index % 16 == 15) "\n" else " "
    }

    return output
}

internal fun String.toHexByteArray(): ByteArray {
    if (length % 2 != 0) throw Exception("Fragmented Byte")

    val arrayLength = length / 2
    val output = ByteArray(arrayLength)

    repeat(arrayLength) {
        val start = it * 2
        output[it] = this.slice(start..start + 1).toInt(16).toByte()
    }

    return output
}
