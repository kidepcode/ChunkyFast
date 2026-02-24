package dev.kidepcode.chunkyfast

import dev.kidepcode.chunkyfast.command.ChunkyFastCommand
import dev.kidepcode.chunkyfast.config.ConfigFacade
import dev.kidepcode.chunkyfast.job.JobManager
import dev.kidepcode.chunkyfast.msg.Messages
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

class ChunkyFastPlugin : JavaPlugin() {

    @Volatile
    lateinit var configFacade: ConfigFacade
        private set

    @Volatile
    lateinit var messages: Messages
        private set

    lateinit var jobManager: JobManager
        private set

    override fun onEnable() {
        saveDefaultConfig()
        reloadAll()

        jobManager = JobManager(this, configFacade.settings, messages)
        jobManager.startTicker()

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            registrar.register(
                "chunkyfast",
                "ChunkyFast",
                listOf("cf", "cfast"),
                ChunkyFastCommand(this)
            )
        }

        if (configFacade.settings.autoResumeOnStart) {
            jobManager.tryAutoResume()
        }
    }

    override fun onDisable() {
        if (::jobManager.isInitialized) {
            jobManager.shutdown()
        }
    }

    fun reloadAll() {
        reloadConfig()
        val facade = ConfigFacade.load(config)
        val msgs = Messages(facade.messages)
        configFacade = facade
        messages = msgs
        if (::jobManager.isInitialized) {
            jobManager.update(configFacade.settings, messages)
        }
    }
}