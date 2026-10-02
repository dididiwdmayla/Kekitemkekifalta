package com.kekitemkekifalta

import android.app.Application
import android.content.Context
import com.kekitemkekifalta.data.BackupRepository
import com.kekitemkekifalta.data.ItemRepository
import com.kekitemkekifalta.data.MarketRepository
import com.kekitemkekifalta.data.PrefsSettingsStore
import com.kekitemkekifalta.data.RoomBackupRepository
import com.kekitemkekifalta.data.RoomItemRepository
import com.kekitemkekifalta.data.RoomMarketRepository
import com.kekitemkekifalta.data.SettingsStore
import com.kekitemkekifalta.data.db.AppDatabase
import com.kekitemkekifalta.notify.SummaryNotifier
import com.kekitemkekifalta.notify.SummaryScheduler
import com.kekitemkekifalta.data.UndoToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow

/** Manual dependency container: everything the screens need, created once. */
class AppContainer(context: Context) {
    val settings: SettingsStore = PrefsSettingsStore(context)
    private val db: AppDatabase = AppDatabase.build(context)
    val items: ItemRepository = RoomItemRepository(db, { settings.householdId })
    val markets: MarketRepository = RoomMarketRepository(db, { settings.householdId })
    val backup: BackupRepository = RoomBackupRepository(db, settings)
    val scheduler = SummaryScheduler(context, settings)

    /** For writes that must finish even if the user leaves the screen right away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Snackbar messages from any screen, shown by the app shell. */
    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
}

sealed interface UiMessage {
    data class Text(val text: String) : UiMessage
    data class Undo(val token: UndoToken) : UiMessage
}

class KekitemApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SummaryNotifier.createChannel(this)
        container.scheduler.ensureScheduled()
    }
}

val Context.container: AppContainer get() = (applicationContext as KekitemApp).container
