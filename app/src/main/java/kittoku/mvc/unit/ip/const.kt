package kittoku.mvc.unit.ip


internal const val IPv4_HEADER_SIZE = 20
internal const val IPv4_VERSION_AND_HEADER_LENGTH: Byte = 0x45
internal const val IPv4_DEFAULT_TTL: Byte = Byte.MAX_VALUE
internal const val IPv4_CORRECT_CHECKSUM: Short = -1

internal const val IP_PROTOCOL_UDP: Byte = 0x11

internal const val IPv4_ADDRESS_SIZE = 4
internal val IPv4_UNKNOWN_ADDRESS: ByteArray = ByteArray(IPv4_ADDRESS_SIZE)
internal val IPv4_BROADCAST_ADDRESS: ByteArray = ByteArray(IPv4_ADDRESS_SIZE) { -1 }
