package com.kekitemkekifalta.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kekitemkekifalta.MainActivity
import com.kekitemkekifalta.R
import com.kekitemkekifalta.UiMessage
import com.kekitemkekifalta.container
import com.kekitemkekifalta.data.isNeed
import com.kekitemkekifalta.notify.SummaryNotifier
import com.kekitemkekifalta.ui.components.KekIcon
import com.kekitemkekifalta.ui.item.ItemScreen
import com.kekitemkekifalta.ui.kekifalta.KekifaltaScreen
import com.kekitemkekifalta.ui.kekitem.KekitemScreen
import com.kekitemkekifalta.ui.market.MarketMapScreen
import com.kekitemkekifalta.ui.market.MarketsScreen
import com.kekitemkekifalta.ui.market.RouteEditorScreen
import com.kekitemkekifalta.ui.market.ShoppingScreen
import com.kekitemkekifalta.ui.settings.SettingsScreen
import com.kekitemkekifalta.ui.theme.KekTheme
import com.kekitemkekifalta.ui.waste.WasteScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object KekitemDest
@Serializable object KekifaltaDest
@Serializable object MarketsDest
@Serializable object SettingsDest
@Serializable object WasteDest
@Serializable data class ItemDest(val id: String)
@Serializable data class MarketDest(val id: String)
@Serializable data class RouteEditorDest(val id: String)
@Serializable data class ShoppingDest(val marketId: String)

private data class Tab(val route: Any, val klass: KClass<*>, val label: String, @DrawableRes val icon: Int)

private val tabs = listOf(
    Tab(KekitemDest, KekitemDest::class, "Kekitem", R.drawable.ic_tab_kekitem),
    Tab(KekifaltaDest, KekifaltaDest::class, "Kekifalta", R.drawable.ic_tab_kekifalta),
    Tab(MarketsDest, MarketsDest::class, "Mercado", R.drawable.ic_tab_market),
    Tab(SettingsDest, SettingsDest::class, "Ajustes", R.drawable.ic_tab_settings),
)

private fun NavHostController.goToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun KekApp(openRequest: String?, onOpenRequestHandled: () -> Unit) {
    val context = LocalContext.current
    val container = remember { context.container }
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val needCount by remember { container.items.observeItems().map { list -> list.count { it.isNeed } } }
        .collectAsStateWithLifecycle(0)

    // Snackbars from every screen; undo actions run here.
    LaunchedEffect(Unit) {
        container.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            launch {
                when (message) {
                    is UiMessage.Text -> snackbar.showSnackbar(message.text, duration = SnackbarDuration.Short)
                    is UiMessage.Undo -> {
                        val result = snackbar.showSnackbar(message.token.label, actionLabel = "Desfazer", duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) container.items.undo(message.token)
                    }
                }
            }
        }
    }

    // Ask once for notification permission (Android 13+), since the daily summary is on by default.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) container.scheduler.reschedule()
    }
    LaunchedEffect(Unit) {
        val settings = container.settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            settings.settings.value.notificationsEnabled &&
            !SummaryNotifier.canPost(context) &&
            !settings.askedNotificationPermission
        ) {
            settings.askedNotificationPermission = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(openRequest) {
        if (openRequest == null) return@LaunchedEffect
        nav.currentBackStackEntryFlow.first() // the graph must exist before navigating
        if (openRequest == MainActivity.OPEN_KEKIFALTA) nav.goToTab(KekifaltaDest)
        onOpenRequestHandled()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = tabs.firstOrNull { tab -> destination?.hasRoute(tab.klass) == true }

    Scaffold(
        containerColor = KekTheme.colors.paper,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .border(2.dp, KekTheme.colors.outline, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = KekTheme.colors.ink,
                    contentColor = KekTheme.colors.paper,
                    action = if (data.visuals.actionLabel != null) {
                        {
                            TextButton(onClick = { data.performAction() }) {
                                Text(data.visuals.actionLabel.orEmpty(), color = KekTheme.colors.mustard, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        null
                    },
                ) {
                    Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        bottomBar = {
            if (currentTab != null) {
                KekBottomBar(current = currentTab, needCount = needCount, onSelect = { nav.goToTab(it.route) })
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = KekitemDest,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable<KekitemDest> {
                KekitemScreen(onOpenItem = { nav.navigate(ItemDest(it)) })
            }
            composable<KekifaltaDest> {
                KekifaltaScreen(
                    onOpenItem = { nav.navigate(ItemDest(it)) },
                    onGoShopping = { nav.navigate(ShoppingDest(it)) },
                )
            }
            composable<MarketsDest> {
                MarketsScreen(onOpenMarket = { nav.navigate(MarketDest(it)) })
            }
            composable<SettingsDest> {
                SettingsScreen(onOpenWaste = { nav.navigate(WasteDest) })
            }
            composable<WasteDest> {
                WasteScreen(onBack = { nav.popBackStack() })
            }
            composable<ItemDest> { entry ->
                ItemScreen(itemId = entry.toRoute<ItemDest>().id, onBack = { nav.popBackStack() })
            }
            composable<MarketDest> { entry ->
                val id = entry.toRoute<MarketDest>().id
                MarketMapScreen(
                    marketId = id,
                    onBack = { nav.popBackStack() },
                    onEditRoute = { nav.navigate(RouteEditorDest(id)) },
                    onStartShopping = { nav.navigate(ShoppingDest(id)) },
                )
            }
            composable<RouteEditorDest> { entry ->
                RouteEditorScreen(
                    marketId = entry.toRoute<RouteEditorDest>().id,
                    onBack = { nav.popBackStack() },
                    onMarketDeleted = { nav.popBackStack<MarketsDest>(inclusive = false) },
                )
            }
            composable<ShoppingDest> { entry ->
                ShoppingScreen(
                    marketId = entry.toRoute<ShoppingDest>().marketId,
                    onBack = { nav.popBackStack() },
                    onSwitchMarket = { other ->
                        nav.navigate(ShoppingDest(other)) {
                            popUpTo<ShoppingDest> { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun KekBottomBar(current: Tab, needCount: Int, onSelect: (Tab) -> Unit) {
    val c = KekTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.paper)
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(c.outline),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = tab == current
                val background by animateColorAsState(if (selected) c.ink else c.paper, label = "tab")
                val content = if (selected) c.paper else c.ink
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(background)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(tab) },
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            KekIcon(tab.icon, tab.label, tint = content, size = 24.dp)
                            if (tab.klass == KekifaltaDest::class && needCount > 0) {
                                Text(
                                    if (needCount > 99) "99+" else "$needCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.onAccent,
                                    modifier = Modifier
                                        .offset(x = 14.dp, y = (-6).dp)
                                        .clip(CircleShape)
                                        .background(c.tomato)
                                        .border(1.5.dp, c.outline, CircleShape)
                                        .padding(horizontal = 5.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
                    }
                }
            }
        }
    }
}
