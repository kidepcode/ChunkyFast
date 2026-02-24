package dev.kidepcode.chunkyfast.util

class LongQueue(initialCapacity: Int) {
    private var a = LongArray(maxOf(16, initialCapacity))
    private var head = 0
    private var tail = 0
    private var size = 0

    fun add(v: Long) {
        if (size == a.size) grow()
        a[tail] = v
        tail++
        if (tail == a.size) tail = 0
        size++
    }

    fun pollOrNull(): Long? {
        if (size == 0) return null
        val v = a[head]
        head++
        if (head == a.size) head = 0
        size--
        return v
    }

    fun isEmpty(): Boolean = size == 0
    fun size(): Int = size

    private fun grow() {
        val n = LongArray(a.size shl 1)
        var i = 0
        while (i < size) {
            n[i] = a[(head + i) % a.size]
            i++
        }
        a = n
        head = 0
        tail = size
    }
}