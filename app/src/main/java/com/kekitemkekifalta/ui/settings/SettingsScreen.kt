@file:OptIn(ExperimentalMaterial3Api::class)

package com.kekitemkekifalta.ui.settings

import android.Manifest
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kekitemkekifalta.AppContainer
import com.kekitemkekifalta.BuildConfig
import com.kekitemkekifalta.R
import com.kekitemkekifalta.UiMessage
import com.kekitemkekifalta.container
import com.kekitemkekifalta.core.BackupFormatException
import com.kekitemkekifalta.data.BackupSummary
import com.kekitemkekifalta.data.ImportMode
import com.kekitemkekifalta.data.Settings
import com.kekitemkekifalta.data.ThemeMode
import com.kekitemkekifalta.notify.SummaryNotifier
import com.kekitemkekifalta.ui.common.formatDateTime
import com.kekitemkekifalta.ui.components.ChoiceChip
import com.kekitemkekifalta.ui.components.KekButton
import com.kekitemkekifalta.ui.components.KekGhostButton
import com.kekitemkekifalta.ui.components.KekIcon
import com.kekitemkekifalta.ui.components.ScreenHeader
import com.kekitemkekifalta.ui.components.StickerCard
import com.kekitemkekifalta.ui.theme.KekTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class PendingImport(val json: String, val summary: BackupSummary)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings> = c.settings.settings

    private val pending = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = pending.asStateFlow()

    fun setNotifications(enabled: Boolean) {
        c.settings.setNotificationsEnabled(enabled)
        c.scheduler.reschedule()
    }

    fun setTime(minuteOfDay: Int) {
        c.settings.setNotificationTime(minuteOfDay)
        c.scheduler.reschedule()
    }

    fun setTheme(mode: ThemeMode) = c.settings.setThemeMode(mode)

    fun previewSummary(context: android.content.Context) = viewModelScope.launch {
        val posted = SummaryNotifier.post(context, c.items.allLiveItems(), force = true)
        if (!posted) c.messages.emit(UiMessage.Text("Sem permissão de notificação. Ative nas configurações do Android."))
    }

    fun exportTo(resolver: ContentResolver, uri: Uri) = c.appScope.launch {
        try {
            val json = c.backup.exportJson()
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } ?: error("sem arquivo")
            }
            c.messages.emit(UiMessage.Text("Backup salvo"))
        } catch (e: Exception) {
            c.messages.emit(UiMessage.Text("Não deu pra salvar o backup: ${e.message}"))
        }
    }

    fun inspect(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        try {
            val json = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("sem arquivo")
            }
            pending.value = PendingImport(json, c.backup.inspect(json))
        } catch (e: BackupFormatException) {
            c.messages.emit(UiMessage.Text(e.message ?: "Arquivo inválido"))
        } catch (e: Exception) {
            c.messages.emit(UiMessage.Text("Não deu pra ler o arquivo: ${e.message}"))
        }
    }

    fun cancelImport() {
        pending.value = null
    }

    fun confirmImport(mode: ImportMode) {
        val current = pending.value ?: return
        pending.value = null
        c.appScope.launch {
            try {
                val summary = c.backup.import(current.json, mode)
                c.messages.emit(UiMessage.Text("Backup importado: ${summary.items} itens, ${summary.markets} mercados"))
            } catch (e: Exception) {
                c.messages.emit(UiMessage.Text("Falhou ao importar: ${e.message}"))
            }
        }
    }
}

@Composable
fun SettingsScreen(onOpenWaste: () -> Unit) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(context.container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pendingImport by vm.pendingImport.collectAsStateWithLifecycle()
    var pickingTime by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotifications(granted)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(context.contentResolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.inspect(context.contentResolver, uri)
    }

    fun toggleNotifications(enabled: Boolean) {
        if (enabled && !SummaryNotifier.canPost(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.setNotifications(enabled)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(title = "ajustes", subtitle = "do jeito da casa")

        SettingsCard(R.drawable.ic_bell, "Resumo do dia") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Uma notificação por dia com o que está acabando.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KekTheme.colors.muted,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { toggleNotifications(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = KekTheme.colors.leaf,
                        checkedThumbColor = KekTheme.colors.card,
                        checkedBorderColor = KekTheme.colors.outline,
                        uncheckedTrackColor = KekTheme.colors.paper,
                        uncheckedThumbColor = KekTheme.colors.outline,
                        uncheckedBorderColor = KekTheme.colors.outline,
                    ),
                )
            }
            if (settings.notificationsEnabled) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KekGhostButton("às ${formatMinute(settings.notificationMinuteOfDay)}", { pickingTime = true }, icon = R.drawable.ic_clock)
                    KekGhostButton("ver agora", { vm.previewSummary(context) }, icon = R.drawable.ic_bell)
                }
            }
        }

        SettingsCard(R.drawable.ic_sparkle, "Tema") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    ChoiceChip(mode.label, selected = settings.themeMode == mode, onClick = { vm.setTheme(mode) })
                }
            }
        }

        SettingsCard(R.drawable.ic_waste, "Desperdício") {
            Text("O que estragou neste mês (e nos anteriores).", style = MaterialTheme.typography.bodyMedium, color = KekTheme.colors.muted)
            Spacer(Modifier.height(10.dp))
            KekButton("Ver desperdício", onOpenWaste, icon = R.drawable.ic_waste)
        }

        SettingsCard(R.drawable.ic_download, "Backup") {
            Text(
                "Todos os dados num arquivo JSON. Serve para passar tudo pro outro celular: exporte aqui, importe lá.",
                style = MaterialTheme.typography.bodyMedium,
                color = KekTheme.colors.muted,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KekButton("Exportar", { exportLauncher.launch("kekitemkekifalta-${LocalDate.now()}.json") }, Modifier.weight(1f), icon = R.drawable.ic_download)
                KekGhostButton("Importar", { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }, Modifier.weight(1f), icon = R.drawable.ic_upload)
            }
        }

        SettingsCard(R.drawable.ic_home_check, "Sobre") {
            Text("kekitemkekifalta ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall, color = KekTheme.colors.ink)
            Text(
                "O que tem e o que falta, feito pra casa. Tudo fica neste celular. Fontes Fredoka e Nunito (SIL Open Font License).",
                style = MaterialTheme.typography.bodySmall,
                color = KekTheme.colors.muted,
            )
        }
    }

    if (pickingTime) {
        TimeDialog(
            initialMinute = settings.notificationMinuteOfDay,
            onDismiss = { pickingTime = false },
            onConfirm = {
                vm.setTime(it)
                pickingTime = false
            },
        )
    }

    pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            containerColor = KekTheme.colors.paper,
            title = { Text("Importar backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup de ${formatDateTime(pending.summary.exportedAt)}: ${pending.summary.items} itens e ${pending.summary.markets} mercados.")
                    Text(
                        "Mesclar: junta com o que já tem aqui (vale a versão mais recente de cada coisa).\nSubstituir: apaga os dados deste celular e fica igual ao backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KekTheme.colors.muted,
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { vm.confirmImport(ImportMode.REPLACE) }) { Text("Substituir", color = KekTheme.colors.tomato) }
                    TextButton(onClick = { vm.confirmImport(ImportMode.MERGE) }) { Text("Mesclar") }
                }
            },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SettingsCard(icon: Int, title: String, content: @Composable () -> Unit) {
    StickerCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KekIcon(icon, null, size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = KekTheme.colors.ink)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KekTheme.colors.paper,
        title = { Text("Horário do resumo") },
        text = { TimeInput(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun formatMinute(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
