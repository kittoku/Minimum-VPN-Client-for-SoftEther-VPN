package kittoku.mvc.service.client.softether


internal const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 6.3; WOW64; rv:29.0) Gecko/20100101 Firefox/29.0"
internal const val HTTP_KEEP_ALIVE = "timeout=15; max=19"

internal const val HTTP_200_HEADER = "HTTP/1.1 200 OK"
internal const val HTTP_DETECT_TAG = "9C37197CA7C2428388C2E6E59B829B30"
internal val HTTP_DETECT_BODY = """<!DOCTYPE HTML PUBLIC "-//IETF//DTD HTML 2.0//EN">
    |<HTML><HEAD>
    |<TITLE>403 Forbidden</TITLE>
    |</HEAD><BODY>
    |<H1>Forbidden</H1>
    |You don't have permission to access """.trimMargin().replace("\n", "\r\n")

internal const val UDP_ACCELERATION_V2_KEY_SIZE = 128
