package dev.kidepcode.chunkyfast.util

object ChunkKey {
    fun pack(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFF_FFFFL)
    }

    fun x(key: Long): Int = (key shr 32).toInt()
    fun z(key: Long): Int = key.toInt()
}