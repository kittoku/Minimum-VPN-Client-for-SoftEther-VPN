package kittoku.mvc.debug


internal class AssertException : Exception("") // not as serious as AssertionError

internal fun assertAlways(value: Boolean) {
    if (!value) {
        throw AssertException()
    }
}

internal fun assertOrThrow(code: ErrorCode, body: () -> Unit) {
    try {
        body()
    } catch (e: AssertException) {
        throw MvcException(code, e)
    }
}

internal class MvcException(code: ErrorCode, cause: Throwable?) : Exception(code.name, cause)

internal enum class ErrorCode {
    TCP_SOCKET_CLOSED,
    SOFTETHER_NEGOTIATION_TIMEOUT,
    SOFTETHER_INVALID_HTTP_MESSAGE,
    SOFTETHER_INVALID_FRAME_PACK,
    SOFTETHER_INVALID_PROPERTY_PACK,
    SOFTETHER_INVALID_PROTOCOL_SERVER,
    SOFTETHER_AUTHENTICATION_FAILED,
    DHCP_NEGOTIATION_TIMEOUT,
    DHCP_INVALID_CONFIGURATION_ASSIGNED,
    ARP_NEGOTIATION_TIMEOUT,
    ARP_INVALID_CONFIGURATION_ASSIGNED,
}
