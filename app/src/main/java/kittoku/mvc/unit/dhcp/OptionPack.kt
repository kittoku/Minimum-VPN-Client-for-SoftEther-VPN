package kittoku.mvc.unit.dhcp

import kittoku.mvc.debug.assertAlways
import kittoku.mvc.extension.toIntAsUByte
import kittoku.mvc.unit.DataUnit
import java.nio.ByteBuffer
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties


internal class OptionPack : DataUnit {
    internal val unknownOptionTags = mutableListOf<Byte>()

    internal var messageType: Byte = 0

    internal var optionSubnetMask: DhcpOptionSubnetMask? = null
    internal var optionRouterAddress: DhcpOptionRouterAddress? = null
    internal var optionDnsServerAddress: DhcpOptionDnsServerAddress? = null
    internal var optionRequestedAddress: DhcpOptionRequestedAddress? = null
    internal var optionDhcpServerAddress: DhcpOptionDhcpServerAddress? = null

    internal var optionLeaseTime: DhcpOptionLeaseTime? = null
    internal var optionParameterList: DhcpOptionParameterList? = null

    override val length: Int
        get() = 4 + validProperties.map { it.length }.sum() // 4 == MessageType + END

    private val validProperties: List<DhcpOption>
        get() = this::class.memberProperties.filter {
            it.name.startsWith("option")
        }.mapNotNull {
            @Suppress("UNCHECKED_CAST")
            it as KProperty1<OptionPack, DhcpOption?>
            it.get(this)
        }

    override fun write(buffer: ByteBuffer) {
        DhcpOptionMessageType().also {
            it.value = messageType
            it.write(buffer)
        }

        validProperties.forEach { it.write(buffer) }

        buffer.put(DHCP_OPTION_END)
    }

    override fun read(buffer: ByteBuffer) {
        unknownOptionTags.clear()

        while (true) {
            when (val tag = buffer.get()) {
                DHCP_OPTION_SUBNET_MASK -> importOption(DhcpOptionSubnetMask(), buffer)
                DHCP_OPTION_ROUTER_ADDRESS -> importOption(DhcpOptionRouterAddress(), buffer)
                DHCP_OPTION_DNS_SERVER_ADDRESS -> importOption(DhcpOptionDnsServerAddress(), buffer)
                DHCP_OPTION_REQUESTED_ADDRESS -> importOption(DhcpOptionRequestedAddress(), buffer)
                DHCP_OPTION_DHCP_SERVER_ADDRESS -> importOption(DhcpOptionDhcpServerAddress(), buffer)
                DHCP_OPTION_LEASE_TIME -> importOption(DhcpOptionLeaseTime(), buffer)
                DHCP_OPTION_PARAMETER_LIST -> importOption(DhcpOptionParameterList(), buffer)
                DHCP_OPTION_MESSAGE_TYPE -> {
                    DhcpOptionMessageType().also {
                        it.read(buffer)
                        messageType = it.value
                    }
                }

                DHCP_OPTION_END -> break

                else -> {
                    unknownOptionTags.add(tag)
                    discardOption(buffer)
                }
            }
        }

        assertAlways(messageType.toIntAsUByte() != 0)
    }

    private fun importOption(option: DhcpOption, buffer: ByteBuffer) {
        val targetPropertyName = "option" + option::class.simpleName!!.substring(10)
        val kProperty = this::class.memberProperties.first { it.name == targetPropertyName }

        @Suppress("UNCHECKED_CAST")
        kProperty as KMutableProperty1<OptionPack, DhcpOption?>

        assertAlways(kProperty.get(this) == null) // avoid key duplication

        option.read(buffer)

        kProperty.set(this, option)
    }

    private fun discardOption(buffer: ByteBuffer) {
        DhcpOption().also {
            it.read(buffer)
        }
    }
}
