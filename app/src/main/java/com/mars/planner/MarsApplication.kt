package com.mars.planner

import android.app.Application
import com.mars.planner.data.TaskRepository
import com.mars.planner.data.db.MarsDatabase
import com.mars.planner.data.prefs.SettingsRepository
import com.mars.planner.reminder.ReminderChannels
import com.mars.planner.reminder.ReminderScheduler
import com.mars.planner.sync.SyncClient

class AppContainer(app: Application) {
    private val db = MarsDatabase.get(app)
    val tasks = TaskRepository(db.taskDao(), db.enhancementDao())
    val settings = SettingsRepository(app)
    val sync = SyncClient()
}

class MarsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderChannels.ensure(this)
        ReminderScheduler.scheduleDigestWorkers(this)
    }
}
