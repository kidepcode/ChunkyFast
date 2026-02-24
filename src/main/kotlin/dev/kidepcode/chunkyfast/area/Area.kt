package dev.kidepcode.chunkyfast.area

import dev.kidepcode.chunkyfast.job.CursorState
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.configuration.ConfigurationSection
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

sealed class Area(
    val type: String,
    val minChunkX: Int,
    val minChunkZ: Int,
    val maxChunkX: Int,
    val maxChunkZ: Int
) {
    abstract fun totalChunks(): Long
    abstract fun createCursor(state: CursorState?): ChunkCursor
    abstract fun serialize(section: ConfigurationSection)

    class Circle(
        val centerX: Int,
        val centerZ: Int,
        val radius: Int
    ) : Area(
        "circle",
        centerX - radius,
        centerZ - radius,
        centerX + radius,
        centerZ + radius
    ) {
        private val r2: Long = radius.toLong() * radius.toLong()

        override fun totalChunks(): Long {
            if (radius <= 0) return 1L
            var sum = 0L
            var dz = -radius
            while (dz <= radius) {
                val dz2 = dz.toLong() * dz.toLong()
                val maxDx = sqrt((r2 - dz2).toDouble()).toInt()
                sum += (maxDx * 2L + 1L)
                dz++
            }
            return sum
        }

        override fun createCursor(state: CursorState?): ChunkCursor {
            return CircleCursor(this, state)
        }

        override fun serialize(section: ConfigurationSection) {
            section.set("type", type)
            section.set("centerX", centerX)
            section.set("centerZ", centerZ)
            section.set("radius", radius)
        }
    }

    class Square(
        val centerX: Int,
        val centerZ: Int,
        val radius: Int
    ) : Area(
        "square",
        centerX - radius,
        centerZ - radius,
        centerX + radius,
        centerZ + radius
    ) {
        override fun totalChunks(): Long {
            val side = radius * 2L + 1L
            return side * side
        }

        override fun createCursor(state: CursorState?): ChunkCursor {
            return RectCursor(minChunkX, minChunkZ, maxChunkX, maxChunkZ, state)
        }

        override fun serialize(section: ConfigurationSection) {
            section.set("type", type)
            section.set("centerX", centerX)
            section.set("centerZ", centerZ)
            section.set("radius", radius)
        }
    }

    class Rect(
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int
    ) : Area(
        "rect",
        min(minX, maxX),
        min(minZ, maxZ),
        max(minX, maxX),
        max(minZ, maxZ)
    ) {
        override fun totalChunks(): Long {
            val w = (maxChunkX.toLong() - minChunkX.toLong() + 1L)
            val h = (maxChunkZ.toLong() - minChunkZ.toLong() + 1L)
            return w * h
        }

        override fun createCursor(state: CursorState?): ChunkCursor {
            return RectCursor(minChunkX, minChunkZ, maxChunkX, maxChunkZ, state)
        }

        override fun serialize(section: ConfigurationSection) {
            section.set("type", type)
            section.set("minX", minChunkX)
            section.set("minZ", minChunkZ)
            section.set("maxX", maxChunkX)
            section.set("maxZ", maxChunkZ)
        }
    }

    class WorldBorderRect(
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int
    ) : Area(
        "worldborder",
        minX,
        minZ,
        maxX,
        maxZ
    ) {
        override fun totalChunks(): Long {
            val w = (maxChunkX.toLong() - minChunkX.toLong() + 1L)
            val h = (maxChunkZ.toLong() - minChunkZ.toLong() + 1L)
            return w * h
        }

        override fun createCursor(state: CursorState?): ChunkCursor {
            return RectCursor(minChunkX, minChunkZ, maxChunkX, maxChunkZ, state)
        }

        override fun serialize(section: ConfigurationSection) {
            section.set("type", type)
            section.set("minX", minChunkX)
            section.set("minZ", minChunkZ)
            section.set("maxX", maxChunkX)
            section.set("maxZ", maxChunkZ)
        }
    }

    companion object {
        fun circle(cx: Int, cz: Int, radius: Int): Area = Circle(cx, cz, radius)
        fun square(cx: Int, cz: Int, radius: Int): Area = Square(cx, cz, radius)
        fun rect(minX: Int, minZ: Int, maxX: Int, maxZ: Int): Area = Rect(minX, minZ, maxX, maxZ)

        fun worldBorder(world: World): Area {
            val b: WorldBorder = world.worldBorder
            val center = b.center
            val half = b.size / 2.0

            val minBlockX = floor(center.x - half).toInt()
            val maxBlockX = floor(center.x + half).toInt()
            val minBlockZ = floor(center.z - half).toInt()
            val maxBlockZ = floor(center.z + half).toInt()

            val minChunkX = floorDiv(minBlockX, 16)
            val maxChunkX = floorDiv(maxBlockX, 16)
            val minChunkZ = floorDiv(minBlockZ, 16)
            val maxChunkZ = floorDiv(maxBlockZ, 16)

            return WorldBorderRect(minChunkX, minChunkZ, maxChunkX, maxChunkZ)
        }

        fun deserialize(section: ConfigurationSection): Area? {
            val type = (section.getString("type") ?: return null).lowercase()
            return when (type) {
                "circle" -> {
                    val cx = section.getInt("centerX")
                    val cz = section.getInt("centerZ")
                    val r = section.getInt("radius")
                    Circle(cx, cz, max(0, r))
                }
                "square" -> {
                    val cx = section.getInt("centerX")
                    val cz = section.getInt("centerZ")
                    val r = section.getInt("radius")
                    Square(cx, cz, max(0, r))
                }
                "rect" -> {
                    val minX = section.getInt("minX")
                    val minZ = section.getInt("minZ")
                    val maxX = section.getInt("maxX")
                    val maxZ = section.getInt("maxZ")
                    Rect(minX, minZ, maxX, maxZ)
                }
                "worldborder" -> {
                    val minX = section.getInt("minX")
                    val minZ = section.getInt("minZ")
                    val maxX = section.getInt("maxX")
                    val maxZ = section.getInt("maxZ")
                    WorldBorderRect(minX, minZ, maxX, maxZ)
                }
                else -> null
            }
        }

        private fun floorDiv(x: Int, y: Int): Int {
            var r = x / y
            if ((x xor y) < 0 && r * y != x) r--
            return r
        }
    }
}