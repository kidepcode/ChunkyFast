package dev.kidepcode.chunkyfast.skip

import java.io.File
import java.io.RandomAccessFile
import java.util.LinkedHashMap

class RegionHeaderCache(
    private val regionDir: File,
    private val maxRegions: Int
) {
    private val headerBuf = ByteArray(4096)

    private val lru = object : LinkedHashMap<Long, RegionBits>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, RegionBits>): Boolean {
            return size > maxRegions
        }
    }

    fun hasChunk(chunkX: Int, chunkZ: Int): Boolean {
        val rx = chunkX shr 5
        val rz = chunkZ shr 5
        val key = pack(rx, rz)

        val bits = lru[key] ?: load(rx, rz).also { lru[key] = it }
        return bits.has(chunkX, chunkZ)
    }

    private fun load(rx: Int, rz: Int): RegionBits {
        val file = File(regionDir, "r.$rx.$rz.mca")
        if (!file.exists()) return RegionBits.EMPTY

        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(0L)
                raf.readFully(headerBuf, 0, 4096)
            }

            val bits = LongArray(16)
            var off = 0
            var i = 0
            while (i < 1024) {
                val b0 = headerBuf[off].toInt() and 0xFF
                val b1 = headerBuf[off + 1].toInt() and 0xFF
                val b2 = headerBuf[off + 2].toInt() and 0xFF
                val loc = (b0 shl 16) or (b1 shl 8) or b2
                if (loc != 0) {
                    val word = i ushr 6
                    bits[word] = bits[word] or (1L shl (i and 63))
                }
                off += 4
                i++
            }

            RegionBits(bits)
        } catch (_: Throwable) {
            RegionBits.EMPTY
        }
    }

    private fun pack(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFF_FFFFL)
    }

    private class RegionBits(
        private val bits: LongArray
    ) {
        fun has(chunkX: Int, chunkZ: Int): Boolean {
            val idx = ((chunkZ and 31) shl 5) or (chunkX and 31)
            val word = idx ushr 6
            val mask = 1L shl (idx and 63)
            return (bits[word] and mask) != 0L
        }

        companion object {
            val EMPTY = RegionBits(LongArray(16))
        }
    }
}