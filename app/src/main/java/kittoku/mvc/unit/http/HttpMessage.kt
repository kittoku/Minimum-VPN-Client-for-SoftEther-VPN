package kittoku.mvc.unit.http

import kittoku.mvc.debug.assertAlways
import kittoku.mvc.unit.DataUnit
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer


private const val CR: Byte = 0x0D
private const val LF: Byte = 0x0A

internal class HttpMessage : DataUnit {
    lateinit var header: String
    var fieldMap = hashMapOf<String, String>()
    var body: ByteArray? = null

    override val length: Int
        get() {
            var sum = 2 // last CRLF

            sum += header.length + 2 // CRLF

            fieldMap.forEach { (key, value) ->
                sum += key.length + value.length + 4 // colon, space, CRLF
            }

            sum += body?.size ?: 0

            return sum
        }

    override fun write(buffer: ByteBuffer) {
        var sum = header + "\r\n"

        fieldMap.forEach { (key, value) ->
            sum += "$key: $value\r\n"
        }

        sum += "\r\n"

        buffer.put(sum.toByteArray(Charsets.US_ASCII))
        body?.also { buffer.put(it) }
    }

    override fun read(buffer: ByteBuffer) {
        buffer.nextCrlf().also {
            header = buffer.array().sliceArray(buffer.position() until it).toString(Charsets.US_ASCII)

            buffer.position(it + 2)
        }


        while (true) {
            val stop = buffer.nextCrlf()
            if (stop == buffer.position()) {
                buffer.position(stop + 2)
                break
            }

            val line = buffer.array().sliceArray(buffer.position() until stop).toString(Charsets.US_ASCII)

            val delimiterIndex = line.indexOf(": ")
            assertAlways(delimiterIndex >= 0)

            val keyString = line.substring(0, delimiterIndex)
            val valueString = line.substring(delimiterIndex + 2)
            if (keyString == "Content-Length") {
                body = ByteArray(valueString.toInt())
            }
            fieldMap[keyString] = valueString

            buffer.position(stop + 2)
        }

        body?.also { buffer.get(it) }
    }

    private fun ByteBuffer.nextCrlf(): Int {
        if (remaining() < 2) {
            throw BufferUnderflowException()
        }

        (position() until limit() - 1).forEach {
            if (get(it) == CR && get(it + 1) == LF) {
                return it
            }
        }

        throw BufferUnderflowException()
    }
}
