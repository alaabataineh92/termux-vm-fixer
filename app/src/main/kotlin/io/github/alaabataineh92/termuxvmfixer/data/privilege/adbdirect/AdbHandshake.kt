package io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

internal object AdbHandshake {

    fun speaksAdb(host: String, port: Int, timeoutMillis: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMillis)
            socket.soTimeout = timeoutMillis

            val payload = CONNECT_PAYLOAD
            val checksum = payload.fold(0) { sum, byte -> sum + (byte.toInt() and 0xFF) }

            DataOutputStream(socket.getOutputStream()).apply {
                writeIntLe(CMD_CNXN)
                writeIntLe(CONNECT_VERSION)
                writeIntLe(CONNECT_MAXDATA)
                writeIntLe(payload.size)
                writeIntLe(checksum)
                writeIntLe(CMD_CNXN xor -1)
                write(payload)
                flush()
            }

            val command = DataInputStream(socket.getInputStream()).readIntLe()
            command == CMD_AUTH || command == CMD_CNXN
        }
    }.getOrDefault(false)

    private fun DataOutputStream.writeIntLe(value: Int) {
        writeInt(Integer.reverseBytes(value))
    }

    private fun DataInputStream.readIntLe(): Int = Integer.reverseBytes(readInt())

    private const val CMD_AUTH = 0x48545541
    private const val CMD_CNXN = 0x4e584e43

    private const val CONNECT_VERSION = 0x01000000
    private const val CONNECT_MAXDATA = 4096

    private val CONNECT_PAYLOAD = "host::\u0000".toByteArray(Charsets.UTF_8)
}
