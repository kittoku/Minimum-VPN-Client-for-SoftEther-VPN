package kittoku.mvc.service.teminal.tcp


internal const val CONTROL_UNIT_WAIT_TIMEOUT = 100
internal const val DATA_UNIT_WAIT_TIMEOUT = 1_000

internal const val TCP_KEEP_ALIVE_TIMEOUT: Int = 20_000
internal const val TCP_KEEP_ALIVE_MIN_INTERVAL = TCP_KEEP_ALIVE_TIMEOUT / 5
internal const val TCP_KEEP_ALIVE_INTERVAL_DIFF = TCP_KEEP_ALIVE_TIMEOUT / 2 - TCP_KEEP_ALIVE_MIN_INTERVAL
