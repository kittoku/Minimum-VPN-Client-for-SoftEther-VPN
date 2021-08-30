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
    internal var sepClientProductName: SepClientProductName? = null
    internal var sepRandom: SepRandom? = null
    internal var sepPenCore: SepPenCore? = null
    internal var sepError: SepError? = null

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
                SEP_CLIENT_PRODUCT_NAME -> importProperty(SepClientProductName(), buffer)
                SEP_RANDOM -> importProperty(SepRandom(), buffer)
                SEP_PEN_CORE -> importProperty(SepPenCore(), buffer)
                SEP_ERROR -> importProperty(SepError(), buffer)
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
