package kittoku.mvc.unit.property

import kittoku.mvc.debug.assertAlways
import kittoku.mvc.extension.move
import kittoku.mvc.unit.DataUnit
import java.nio.ByteBuffer
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties


internal class PropertyPack : DataUnit {
    internal val unknownPropertyKeys = mutableListOf<String>()

    internal var sepBuild: SepBuild? = null
    internal var sepVersion: SepVersion? = null
    internal var sepUniqueIDCamel: SepUniqueIDCamel? = null
    internal var sepUniqueIDSnake: SepUniqueIDSnake? = null
    internal var sepClientBuild: SepClientBuild? = null
    internal var sepClientID: SepClientID? = null
    internal var sepClientStr: SepClientStr? = null
    internal var sepClientVersion: SepClientVersion? = null
    internal var sepClientHostname: SepClientHostname? = null
    internal var sepClientIPAddress: SepClientIPAddress? = null
    internal var sepClientOSName: SepClientOSName? = null
    internal var sepClientOSVersion: SepClientOSVersion? = null
    internal var sepClientPort: SepClientPort? = null
    internal var sepClientProductBuild: SepClientProductBuild? = null
    internal var sepClientProductName: SepClientProductName? = null
    internal var sepClientProductVersion: SepClientProductVersion? = null
    
    internal var sepHello: SepHello? = null
    
    internal var sepProxyIPAddress: SepProxyIPAddress? =null
    internal var sepProxyPort: SepProxyPort? = null

    internal var sepServerHostname: SepServerHostname? = null
    internal var sepServerIPAddress: SepServerIPAddress? = null
    internal var sepServerPort: SepServerPort2? = null
    internal var sepServerProductBuild: SepServerProductBuild? = null
    internal var sepServerProductName: SepServerProductName? = null
    internal var sepServerProductVersion: SepServerProductVersion? = null

    internal var sepMethod: SepMethod? = null
    internal var sepAuthType: SepAuthType? = null
    internal var sepUsername: SepUsername? = null
    internal var sepSecurePassword: SepSecurePassword? = null
    internal var sepProtocol: SepProtocol? = null
    internal var sepHubName: SepHubName? = null
    internal var sepUseEncrypt: SepUseEncrypt? = null
    internal var sepUseCompress: SepUseCompress? = null
    internal var sepMaxConnection: SepMaxConnection? = null
    internal var sepHalfConnection: SepHalfConnection? = null
    internal var sepRandom: SepRandom? = null
    internal var sepPenCore: SepPenCore? = null
    internal var sepError: SepError? = null

    internal var sepUseUDPAcceleration: SepUseUDPAcceleration? = null
    internal var sepUDPUseEncryption: SepUDPUseEncryption? = null
    internal var sepUDPVersion: SepUDPVersion? = null
    internal var sepUDPMaxVersion: SepUDPMaxVersion? = null
    internal var sepUDPClientIP: SepUDPClientIP? = null
    internal var sepUDPClientPort: SepUDPClientPort? = null
    internal var sepUDPClientKeyV2: SepUDPClientKeyV2? = null
    internal var sepUDPClientCookie: SepUDPClientCookie? = null
    internal var sepUDPServerIP: SepUDPServerIP? = null
    internal var sepUDPServerPort: SepUDPServerPort? = null
    internal var sepUDPServerKeyV2: SepUDPServerKeyV2? = null
    internal var sepUDPServerCookie: SepUDPServerCookie? = null
    internal var sepUDPSupportFastDisconnectDetect: SepUDPSupportFastDisconnectDetect? = null
    internal var sepUDPEnableFastDisconnectDetect: SepUDPEnableFastDisconnectDetect? = null

    override val length: Int
        get() = Int.SIZE_BYTES + validProperties.map { it.length }.sum()

    private val validProperties: List<SoftEtherProperty>
        get() = this::class.memberProperties.filter {
            it.name.startsWith("sep")
        }.mapNotNull {
            @Suppress("UNCHECKED_CAST")
            it as KProperty1<PropertyPack, SoftEtherProperty?>
            it.get(this)
        }

    override fun write(buffer: ByteBuffer) {
        validProperties.also {
            buffer.putInt(it.size)
            it.forEach { sep ->
                sep.write(buffer)
            }
        }
    }

    override fun read(buffer: ByteBuffer) {
        val propertyNum = buffer.int
        assertAlways(propertyNum >= 0)

        unknownPropertyKeys.clear()

        repeat(propertyNum) {
            val keySize = buffer.int - 1
            assertAlways(keySize >= 0)

            val key = ByteArray(keySize).let {
                buffer.get(it)
                it.toString(Charsets.US_ASCII)
            }

            when (key) {
                SEP_BUILD -> importProperty(SepBuild(), buffer)
                SEP_VERSION -> importProperty(SepVersion(), buffer)
                SEP_UNIQUE_ID_CAMEL -> importProperty(SepUniqueIDCamel(), buffer)
                SEP_UNIQUE_ID_SNAKE -> importProperty(SepUniqueIDSnake(), buffer)
                SEP_CLIENT_BUILD -> importProperty(SepClientBuild(), buffer)
                SEP_CLIENT_ID -> importProperty(SepClientID(), buffer)
                SEP_CLIENT_STR -> importProperty(SepClientStr(), buffer)
                SEP_CLIENT_VER -> importProperty(SepClientVersion(), buffer)
                SEP_CLIENT_HOSTNAME -> importProperty(SepClientHostname(), buffer)
                SEP_CLIENT_IP_ADDRESS -> importProperty(SepClientIPAddress(), buffer)
                SEP_CLIENT_OS_NAME -> importProperty(SepClientOSName(), buffer)
                SEP_CLIENT_OS_VER -> importProperty(SepClientOSVersion(), buffer)
                SEP_CLIENT_PORT -> importProperty(SepClientPort(), buffer)
                SEP_CLIENT_PRODUCT_BUILD -> importProperty(SepClientProductBuild(), buffer)
                SEP_CLIENT_PRODUCT_NAME -> importProperty(SepClientProductName(), buffer)
                SEP_CLIENT_PRODUCT_VER -> importProperty(SepClientProductVersion(), buffer)
                SEP_HELLO -> importProperty(SepHello(), buffer)

                SEP_PROXY_IP_ADDRESS -> importProperty(SepProxyIPAddress(), buffer)
                SEP_PROXY_PORT -> importProperty(SepProxyPort(), buffer)

                SEP_SERVER_HOSTNAME -> importProperty(SepServerHostname(), buffer)
                SEP_SERVER_IP_ADDRESS -> importProperty(SepServerIPAddress(), buffer)
                SEP_SERVER_PORT2 -> importProperty(SepServerPort2(), buffer)
                SEP_SERVER_PRODUCT_BUILD -> importProperty(SepServerProductBuild(), buffer)
                SEP_SERVER_PRODUCT_NAME -> importProperty(SepServerProductName(), buffer)
                SEP_SERVER_PRODUCT_VER -> importProperty(SepServerProductVersion(), buffer)

                SEP_METHOD -> importProperty(SepMethod(), buffer)
                SEP_AUTH_TYPE -> importProperty(SepAuthType(), buffer)
                SEP_USERNAME -> importProperty(SepUsername(), buffer)
                SEP_SECURE_PASSWORD -> importProperty(SepSecurePassword(), buffer)
                SEP_PROTOCOL -> importProperty(SepProtocol(), buffer)
                SEP_HUB_NAME -> importProperty(SepHubName(), buffer)
                SEP_USE_ENCRYPT -> importProperty(SepUseEncrypt(), buffer)
                SEP_USE_COMPRESS -> importProperty(SepUseCompress(), buffer)
                SEP_MAX_CONNECTION -> importProperty(SepMaxConnection(), buffer)
                SEP_HALF_CONNECTION -> importProperty(SepHalfConnection(), buffer)
                SEP_RANDOM -> importProperty(SepRandom(), buffer)
                SEP_PEN_CORE -> importProperty(SepPenCore(), buffer)
                SEP_ERROR -> importProperty(SepError(), buffer)

                SEP_USE_UDP_ACCELERATION -> importProperty(SepUseUDPAcceleration(), buffer)
                SEP_UDP_USE_ENCRYPTION -> importProperty(SepUDPUseEncryption(), buffer)
                SEP_UDP_VERSION -> importProperty(SepUDPVersion(), buffer)
                SEP_UDP_MAX_VERSION -> importProperty(SepUDPMaxVersion(), buffer)
                SEP_UDP_CLIENT_IP -> importProperty(SepUDPClientIP(), buffer)
                SEP_UDP_CLIENT_PORT -> importProperty(SepUDPClientPort(), buffer)
                SEP_UDP_CLIENT_KEY_V2 -> importProperty(SepUDPClientKeyV2(), buffer)
                SEP_UDP_CLIENT_COOKIE -> importProperty(SepUDPClientCookie(), buffer)
                SEP_UDP_SERVER_IP -> importProperty(SepUDPServerIP(), buffer)
                SEP_UDP_SERVER_PORT -> importProperty(SepUDPServerPort(), buffer)
                SEP_UDP_SERVER_KEY_V2 -> importProperty(SepUDPServerKeyV2(), buffer)
                SEP_UDP_SERVER_COOKIE -> importProperty(SepUDPServerCookie(), buffer)
                SEP_UDP_SUPPORT_FAST_DISCONNECT_DETECT -> importProperty(SepUDPSupportFastDisconnectDetect(), buffer)
                SEP_UDP_ENABLE_FAST_DISCONNECT_DETECT -> importProperty(SepUDPEnableFastDisconnectDetect(), buffer)

                else -> {
                    unknownPropertyKeys.add(key)
                    discardProperty(buffer)
                }
            }
        }
    }

    private fun importProperty(property: SoftEtherProperty, buffer: ByteBuffer) {
        val targetPropertyName = "sep" + property::class.simpleName!!.substring(3)
        val kProperty = this::class.memberProperties.first { it.name == targetPropertyName }

        @Suppress("UNCHECKED_CAST")
        kProperty as KMutableProperty1<PropertyPack, SoftEtherProperty?>

        assertAlways(kProperty.get(this) == null) // avoid key duplication

        property.read(buffer)

        kProperty.set(this, property)
    }

    private fun discardProperty(buffer: ByteBuffer) {
        val unknownProperty = when (buffer.int) {
            SEP_INT_TYPE -> SoftEtherIntProperty()
            SEP_BYTES_TYPE -> SoftEtherBytesProperty()
            SEP_ASCII_TYPE -> SoftEtherAsciiProperty()
            SEP_UTF8_TYPE -> SoftEtherUtf8Property()
            SEP_LONG_TYPE -> SoftEtherLongProperty()
            else -> throw NotImplementedError()
        }

        buffer.move(-Int.SIZE_BYTES)

        unknownProperty.read(buffer)
    }
}
