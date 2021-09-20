package kittoku.mvc.service.teminal.ip

import kittoku.mvc.service.teminal.udp.UDP_MAX_PAYLOAD_SIZE
import kittoku.mvc.unit.ethernet.ETHERNET_HEADER_SIZE


// 1454 is the limitation of NTT
internal const val IP_MTU = UDP_MAX_PAYLOAD_SIZE - ETHERNET_HEADER_SIZE
