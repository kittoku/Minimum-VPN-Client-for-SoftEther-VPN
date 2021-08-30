package kittoku.mvc.service.client

import kittoku.mvc.service.teminal.ip.IP_MTU
import kittoku.mvc.unit.ethernet.ETHERNET_HEADER_SIZE


internal const val SOFTETHER_NEGOTIATION_TIMEOUT: Long = 30_000
internal const val KEEP_ALIVE_INIT_TIMEOUT: Long = 3_000
internal const val DHCP_NEGOTIATION_TIMEOUT: Long = 30_000
internal const val ARP_NEGOTIATION_TIMEOUT: Long = 30_000

internal const val ETHERNET_MTU = ETHERNET_HEADER_SIZE + IP_MTU

internal const val UDP_PORT_ECHO: Short = 7
