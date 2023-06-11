import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

data class Packet(val name: String, val args: Array<String>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (name != other.name) return false
        return args.contentEquals(other.args)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "$name|${args.joinToString("|")}"
    }
}

class PacketInput(private val readIn: InputStream) {
    private var cache = ""
    fun readPacket(): Packet {
        while (!cache.contains('\n')) {
            val bytes = readIn.read()
            if (bytes == -1) throw IOException("Closed")
            cache += String(byteArrayOf(bytes.toByte()))
        }
        val idx = cache.indexOf('\n')
        val packet = cache.substring(0, idx)
        cache = cache.substring(idx + 1)
        val t = packet.split('|')
        val name = t[0]
        val args = t.subList(1, t.size)
        return Packet(name, args.toTypedArray<String>())
    }
}

class PacketOutput(private val realOut: OutputStream) {
    fun write(name: String, vararg args: Any) {
        val s = formatPacket(name, *args) + "\n"
        realOut.write(s.encodeToByteArray())
        realOut.flush()
    }
}