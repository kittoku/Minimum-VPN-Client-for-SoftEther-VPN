package kittoku.mvc.unit.dhcp

import kittoku.mvc.debug.assertAlways
import kittoku.mvc.extension.toIntAsUByte
import java.nio.ByteBuffer


internal abstract class DhcpAddressOption : DhcpOption() {
    internal val address = ByteArray(4)

    override val lengthWithoutHeader = address.size

    override fun write(buffer: ByteBuffer) {
        writeHeader(buffer)
        buffer.put(address)
    }

    override fun read(buffer: ByteBuffer) {
        assertAlways(buffer.get().toIntAsUByte() == lengthWithoutHeader)
        buffer.get(address)
    }
}

internal class DhcpOptionSubnetMask : DhcpAddressOption() {
    override val tag = DHCP_OPTION_SUBNET_MASK
}

internal class DhcpOptionRouterAddress : DhcpAddressOption() {
    override val tag = DHCP_OPTION_ROUTER_ADDRESS
}

internal class DhcpOptionDnsServerAddress : DhcpAddressOption() {
    override val tag = DHCP_OPTION_DNS_SERVER_ADDRESS
}

internal class DhcpOptionRequestedAddress : DhcpAddressOption() {
    override val tag = DHCP_OPTION_REQUESTED_ADDRESS
}

internal class DhcpOptionDhcpServerAddress : DhcpAddressOption() {
    override val tag = DHCP_OPTION_DHCP_SERVER_ADDRESS
}
