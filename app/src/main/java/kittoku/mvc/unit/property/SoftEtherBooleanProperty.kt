package kittoku.mvc.unit.property

import kittoku.mvc.debug.assertAlways
import java.nio.ByteBuffer


internal open class SoftEtherBooleanProperty : SoftEtherProperty() {
    override val valueType = SEP_INT_TYPE
    override val valueNum = 1
    override val length: Int
        get() = headerLength + Int.SIZE_BYTES

    internal var value = false

    override fun write(buffer: ByteBuffer) {
        writeHeader(buffer)
        buffer.putInt(if (value) 1 else 0)
    }

    override fun read(buffer: ByteBuffer) {
        assertAlways(buffer.int == valueType)
        assertAlways(buffer.int == valueNum)

        value = buffer.int != 0
    }
}

internal class SepUseUDPAcceleration : SoftEtherBooleanProperty() {
    override val key = SEP_USE_UDP_ACCELERATION
}

internal class SepUDPUseEncryption : SoftEtherBooleanProperty() {
    override val key = SEP_UDP_USE_ENCRYPTION
}

internal class SepUDPSupportFastDisconnectDetect : SoftEtherBooleanProperty() {
    override val key = SEP_UDP_SUPPORT_FAST_DISCONNECT_DETECT
}

internal class SepUDPEnableFastDisconnectDetect : SoftEtherBooleanProperty() {
    override val key = SEP_UDP_ENABLE_FAST_DISCONNECT_DETECT
}
