package kittoku.mvc.unit.property

import kittoku.mvc.debug.assertAlways
import java.nio.ByteBuffer


internal open class SoftEtherIntProperty : SoftEtherProperty() {
    override val valueType = SEP_INT_TYPE
    override val valueNum = 1
    override val length: Int
        get() = headerLength + Int.SIZE_BYTES

    internal var value = 0

    override fun write(buffer: ByteBuffer) {
        writeHeader(buffer)
        buffer.putInt(value)
    }

    override fun read(buffer: ByteBuffer) {
        assertAlways(buffer.int == valueType)
        assertAlways(buffer.int == valueNum)

        value = buffer.int
    }
}

internal class SepAuthType : SoftEtherIntProperty() {
    override val key = SEP_AUTH_TYPE
}

internal class SepProtocol : SoftEtherIntProperty() {
    override val key = SEP_PROTOCOL
}

internal class SepUseEncrypt : SoftEtherIntProperty() {
    override val key = SEP_USE_ENCRYPT
}

internal class SepUseCompress : SoftEtherIntProperty() {
    override val key = SEP_USE_COMPRESS
}

internal class SepMaxConnection : SoftEtherIntProperty() {
    override val key = SEP_MAX_CONNECTION
}

internal class SepHalfConnection : SoftEtherIntProperty() {
    override val key = SEP_HALF_CONNECTION
}

internal class SepError : SoftEtherIntProperty() {
    override val key = SEP_ERROR
}

internal class SepUDPVersion : SoftEtherIntProperty() {
    override val key = SEP_UDP_VERSION
}

internal class SepUDPMaxVersion : SoftEtherIntProperty() {
    override val key = SEP_UDP_MAX_VERSION
}

internal class SepUDPClientPort : SoftEtherIntProperty() {
    override val key = SEP_UDP_CLIENT_PORT
}

internal class SepUDPServerPort : SoftEtherIntProperty() {
    override val key = SEP_UDP_SERVER_PORT
}

internal class SepUDPClientCookie : SoftEtherIntProperty() {
    override val key = SEP_UDP_CLIENT_COOKIE
}

internal class SepUDPServerCookie : SoftEtherIntProperty() {
    override val key = SEP_UDP_SERVER_COOKIE
}
