package dev.kidepcode.chunkyfast.area

import dev.kidepcode.chunkyfast.job.CursorState
import dev.kidepcode.chunkyfast.util.ChunkKey
import kotlin.math.abs
import kotlin.math.sqrt

interface ChunkCursor {
    fun nextKey(): Long
    fun isFinished(): Boolean
    fun state(): CursorState

    companion object {
        const val END: Long = Long.MIN_VALUE
    }
}

class RectCursor(
    private val minX: Int,
    private val minZ: Int,
    private val maxX: Int,
    private val maxZ: Int,
    state: CursorState?
) : ChunkCursor {

    private var regionX: Int
    private var regionZ: Int
    private var localX: Int
    private var localZ: Int
    private var finished: Boolean = false

    private val minRegionX = floorDiv(minX, 32)
    private val maxRegionX = floorDiv(maxX, 32)
    private val minRegionZ = floorDiv(minZ, 32)
    private val maxRegionZ = floorDiv(maxZ, 32)

    init {
        if (state != null) {
            regionX = state.regionX
            regionZ = state.regionZ
            localX = state.localX
            localZ = state.localZ
            finished = state.finished
        } else {
            regionX = minRegionX
            regionZ = minRegionZ
            localX = 0
            localZ = 0
        }
    }

    override fun nextKey(): Long {
        if (finished) return ChunkCursor.END
        while (true) {
            if (regionZ > maxRegionZ) {
                finished = true
                return ChunkCursor.END
            }
            if (regionX > maxRegionX) {
                regionX = minRegionX
                regionZ++
                localX = 0
                localZ = 0
                continue
            }

            val cx = (regionX shl 5) + localX
            val cz = (regionZ shl 5) + localZ

            advance()

            if (cx < minX || cx > maxX || cz < minZ || cz > maxZ) continue
            return ChunkKey.pack(cx, cz)
        }
    }

    override fun isFinished(): Boolean = finished

    override fun state(): CursorState = CursorState(regionX, regionZ, localX, localZ, finished)

    private fun advance() {
        localX++
        if (localX >= 32) {
            localX = 0
            localZ++
            if (localZ >= 32) {
                localZ = 0
                regionX++
            }
        }
    }

    private fun floorDiv(x: Int, y: Int): Int {
        var r = x / y
        if ((x xor y) < 0 && r * y != x) r--
        return r
    }
}

class CircleCursor(
    private val area: Area.Circle,
    state: CursorState?
) : ChunkCursor {

    private val rect = RectCursor(area.minChunkX, area.minChunkZ, area.maxChunkX, area.maxChunkZ, state)

    private val r2: Long = area.radius.toLong() * area.radius.toLong()
    private var currentZ = Int.MIN_VALUE
    private var rowMinX = 1
    private var rowMaxX = 0

    override fun nextKey(): Long {
        while (true) {
            val key = rect.nextKey()
            if (key == ChunkCursor.END) return ChunkCursor.END
            val cx = ChunkKey.x(key)
            val cz = ChunkKey.z(key)

            if (cz != currentZ) {
                currentZ = cz
                val dz = (cz - area.centerZ)
                if (abs(dz) > area.radius) {
                    rowMinX = 1
                    rowMaxX = 0
                } else {
                    val dz2 = dz.toLong() * dz.toLong()
                    val maxDx = sqrt((r2 - dz2).toDouble()).toInt()
                    rowMinX = area.centerX - maxDx
                    rowMaxX = area.centerX + maxDx
                }
            }

            if (cx < rowMinX || cx > rowMaxX) continue
            return key
        }
    }

    override fun isFinished(): Boolean = rect.isFinished()
    override fun state(): CursorState = rect.state()
}