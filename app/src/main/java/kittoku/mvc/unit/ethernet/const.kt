package kittoku.mvc.unit.ethernet


internal const val ETHER_TYPE_IPv4: Short = 0x0800
internal const val ETHER_TYPE_ARP: Short = 0x0806

internal const val ETHERNET_MAC_ADDRESS_SIZE = 6
internal val ETHERNET_UNKNOWN_ADDRESS: ByteArray = ByteArray(ETHERNET_MAC_ADDRESS_SIZE)
internal val ETHERNET_BROADCAST_ADDRESS: ByteArray = ByteArray(ETHERNET_MAC_ADDRESS_SIZE) { -1 }

internal const val ETHERNET_HEADER_SIZE = 2 * ETHERNET_MAC_ADDRESS_SIZE + Short.SIZE_BYTES
