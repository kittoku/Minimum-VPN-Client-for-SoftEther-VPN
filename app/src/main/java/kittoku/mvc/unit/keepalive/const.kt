package kittoku.mvc.unit.keepalive


internal const val KEEP_ALIVE_MAX_SIZE = 512
internal const val KEEP_ALIVE_FRAME_NUM = -1

internal val KEEP_ALIVE_NATT_PORT_TAG = "NATT_MY_PORT".toByteArray(Charsets.US_ASCII)
internal val KEEP_ALIVE_NATT_IP_TAG = "NATT_MY_IP".toByteArray(Charsets.US_ASCII)
internal val KEEP_ALIVE_NATT_INFO_SIZE = KEEP_ALIVE_NATT_PORT_TAG.size + Short.SIZE_BYTES + KEEP_ALIVE_NATT_IP_TAG.size + 16
