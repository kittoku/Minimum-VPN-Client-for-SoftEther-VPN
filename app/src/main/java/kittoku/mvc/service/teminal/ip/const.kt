package kittoku.mvc.service.teminal.ip

import kittoku.mvc.unit.ethernet.ETHERNET_HEADER_SIZE
import kittoku.mvc.unit.ip.IPv4_HEADER_SIZE
import kittoku.mvc.unit.udp.UDP_HEADER_SIZE


internal const val IP_MTU = 1500 - IPv4_HEADER_SIZE - UDP_HEADER_SIZE - ETHERNET_HEADER_SIZE // for udp acceleration
