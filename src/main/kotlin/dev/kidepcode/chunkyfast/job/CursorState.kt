package dev.kidepcode.chunkyfast.job

import org.bukkit.configuration.ConfigurationSection

data class CursorState(
    val regionX: Int,
    val regionZ: Int,
    val localX: Int,
    val localZ: Int,
    val finished: Boolean
) {
    fun serialize(section: ConfigurationSection) {
        section.set("regionX", regionX)
        section.set("regionZ", regionZ)
        section.set("localX", localX)
        section.set("localZ", localZ)
        section.set("finished", finished)
    }

    companion object {
        fun deserialize(section: ConfigurationSection): CursorState {
            return CursorState(
                regionX = section.getInt("regionX"),
                regionZ = section.getInt("regionZ"),
                localX = section.getInt("localX"),
                localZ = section.getInt("localZ"),
                finished = section.getBoolean("finished", false)
            )
        }
    }
}