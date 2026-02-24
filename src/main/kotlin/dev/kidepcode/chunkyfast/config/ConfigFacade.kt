package dev.kidepcode.chunkyfast.config

import dev.kidepcode.chunkyfast.skip.SkipMode
import org.bukkit.configuration.Configuration

data class BaseLimits(
    val maxConcurrent: Int,
    val dispatchPerTick: Int,
    val completePerTick: Int,
    val unloadPerTick: Int
)

data class LimiterSettings(
    val enabled: Boolean,
    val msptTarget: Double,
    val hardPauseMspt: Double,
    val resumeMspt: Double,
    val minFactor: Double,
    val factorCap: Double,
    val upPerSecond: Double,
    val downPerSecond: Double
)

data class Settings(
    val baseLimits: BaseLimits,
    val urgentChunkRequests: Boolean,
    val unloadMode: UnloadMode,
    val skipMode: SkipMode,
    val regionCacheSize: Int,
    val limiter: LimiterSettings,
    val autosaveSeconds: Int,
    val autoResumeOnStart: Boolean,
    val logProgressSeconds: Int
)

enum class UnloadMode {
    REQUEST,
    IMMEDIATE,
    NONE;

    companion object {
        fun parse(raw: String): UnloadMode {
            return when (raw.trim().uppercase()) {
                "REQUEST" -> REQUEST
                "IMMEDIATE" -> IMMEDIATE
                "NONE" -> NONE
                else -> REQUEST
            }
        }
    }
}

data class MessageBundle(
    val prefix: String,
    val values: Map<String, Any>
)

data class ConfigFacade(
    val settings: Settings,
    val messages: MessageBundle
) {
    companion object {
        fun load(cfg: Configuration): ConfigFacade {
            val s = cfg.getConfigurationSection("settings") ?: cfg.createSection("settings")
            val base = s.getConfigurationSection("baseLimits") ?: s.createSection("baseLimits")
            val lim = s.getConfigurationSection("limiter") ?: s.createSection("limiter")
            val msgs = cfg.getConfigurationSection("messages") ?: cfg.createSection("messages")

            val maxConcurrent = base.getInt("maxConcurrent", 768).coerceIn(1, 8192)
            val dispatchPerTick = base.getInt("dispatchPerTick", 8192).coerceIn(1, 1_000_000)
            val completePerTick = base.getInt("completePerTick", 4096).coerceIn(1, 1_000_000)
            val unloadPerTick = base.getInt("unloadPerTick", 8192).coerceIn(0, 1_000_000)

            val urgent = s.getBoolean("urgentChunkRequests", false)
            val unloadMode = UnloadMode.parse(s.getString("unloadMode", "REQUEST") ?: "REQUEST")

            val skipMode = SkipMode.parse(s.getString("skipMode", "NONE") ?: "NONE")
            val regionCacheSize = s.getInt("regionCacheSize", 1024).coerceIn(0, 100_000)

            val limiter = LimiterSettings(
                enabled = lim.getBoolean("enabled", true),
                msptTarget = lim.getDouble("msptTarget", 25.0).coerceIn(1.0, 49.0),
                hardPauseMspt = lim.getDouble("hardPauseMspt", 45.0).coerceIn(1.0, 200.0),
                resumeMspt = lim.getDouble("resumeMspt", 30.0).coerceIn(1.0, 200.0),
                minFactor = lim.getDouble("minFactor", 0.25).coerceIn(0.05, 1.0),
                factorCap = lim.getDouble("factorCap", 1.0).coerceIn(0.05, 1.0),
                upPerSecond = lim.getDouble("upPerSecond", 0.40).coerceIn(0.01, 10.0),
                downPerSecond = lim.getDouble("downPerSecond", 2.00).coerceIn(0.01, 50.0)
            )

            val autosave = s.getInt("autosaveSeconds", 30).coerceIn(5, 3600)
            val autoResume = s.getBoolean("autoResumeOnStart", true)
            val logProgress = s.getInt("logProgressSeconds", 10).coerceIn(0, 3600)

            val prefix = msgs.getString("prefix", "<gray>[<green>ChunkyFast</green>]</gray> ") ?: ""

            val values = HashMap<String, Any>(64)
            for (k in msgs.getKeys(false)) {
                values[k] = msgs.get(k) ?: continue
            }
            values["prefix"] = prefix

            return ConfigFacade(
                settings = Settings(
                    baseLimits = BaseLimits(
                        maxConcurrent = maxConcurrent,
                        dispatchPerTick = dispatchPerTick,
                        completePerTick = completePerTick,
                        unloadPerTick = unloadPerTick
                    ),
                    urgentChunkRequests = urgent,
                    unloadMode = unloadMode,
                    skipMode = skipMode,
                    regionCacheSize = regionCacheSize,
                    limiter = limiter,
                    autosaveSeconds = autosave,
                    autoResumeOnStart = autoResume,
                    logProgressSeconds = logProgress
                ),
                messages = MessageBundle(prefix = prefix, values = values)
            )
        }
    }
}