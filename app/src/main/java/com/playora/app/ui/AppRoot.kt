package com.playora.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.pm.PackageManager
import com.playora.app.data.MediaRepo
import com.playora.app.data.Settings
import com.playora.app.playback.Playback

/** Navigation destinations that open on top of the bottom-tab scaffold. */
sealed interface Route {
    data object Search : Route
    data object Url : Route
    data object Theme : Route
    data object About : Route
    data object Credits : Route
    data object Privacy : Route
    data object Terms : Route
    data object Donate : Route
    data object Equalizer : Route
    data object NowPlaying : Route
    data object Playlists : Route
    data class PlaylistDetail(val id: Long, val name: String) : Route
    data object Favorites : Route
    data object History : Route
    data object ContinueWatching : Route
    data class FolderDetail(val name: String) : Route
    data object Storage : Route
}

class Nav {
    val stack = mutableStateListOf<Route>()
    fun push(r: Route) { if (stack.lastOrNull() != r) stack.add(r) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
}

private fun hasMediaPermission(ctx: android.content.Context): Boolean {
    // On Android 14+ "selected photos and videos" is a partial grant; any granted media permission is enough to list.
    return MediaRepo.permissionsNeeded().any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaPermission(ctx)) }
    var onboarded by remember { mutableStateOf(Settings.onboarded) }
    val vm: LibVM = viewModel()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaPermission(ctx)
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!onboarded || !granted) {
            Onboarding(
                granted = granted,
                onFinishIntro = { Settings.onboarded = true; onboarded = true },
                onRequest = { permLauncher.launch(MediaRepo.permissionsNeeded()) },
            )
        } else {
            // Re-scan when returning to the app so newly added media appears; cheap enough off-thread.
            LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose {} }
            MainScaffold(vm)
        }
    }
}

@Composable
private fun MainScaffold(vm: LibVM) {
    val nav = remember { Nav() }
    var tab by rememberSaveable { mutableIntStateOf(Settings.startTab) }
    val top = nav.stack.lastOrNull()
    BackHandler(enabled = top != null) { nav.pop() }
    BackHandler(enabled = top == null && tab != 0) { tab = 0 }

    Box(Modifier.fillMaxSize()) {
        // Tabs stay composed under overlays so scroll and search state are preserved.
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeScreen(vm, nav) { tab = it }
                    1 -> VideoLibraryScreen(vm, nav)
                    2 -> MusicLibraryScreen(vm, nav)
                    3 -> FoldersScreen(vm, nav)
                    else -> SettingsScreen(nav)
                }
            }
            MiniPlayer { nav.push(Route.NowPlaying) }
            NavigationBar {
                listOf(
                    "Home" to Icons.Rounded.Home, "Videos" to Icons.Rounded.VideoLibrary, "Music" to Icons.Rounded.MusicNote,
                    "Folders" to Icons.Rounded.Folder, "Settings" to Icons.Rounded.Settings,
                ).forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(tab == i, { tab = i }, { Icon(icon, label) }, label = { Text(label) })
                }
            }
        }
        if (top != null) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Overlay(top, vm, nav)
            }
        }
    }
}

@Composable
private fun Overlay(route: Route, vm: LibVM, nav: Nav) {
    val back = { nav.pop() }
    when (route) {
        Route.Search -> SearchScreen(vm, back)
        Route.Url -> UrlScreen(back, nav)
        Route.Theme -> ThemeScreen(back)
        Route.About -> AboutScreen(back, nav)
        Route.Credits -> CreditsScreen(back)
        Route.Privacy -> DocScreen("Privacy Policy", PRIVACY_TEXT, back)
        Route.Terms -> DocScreen("Terms and Conditions", TERMS_TEXT, back)
        Route.Donate -> DonateScreen(back)
        Route.Equalizer -> EqualizerScreen(back)
        Route.NowPlaying -> NowPlayingScreen(back, nav)
        Route.Playlists -> PlaylistsScreen(vm, nav, back)
        is Route.PlaylistDetail -> PlaylistDetailScreen(route.id, route.name, vm, back)
        Route.Favorites -> FavoritesScreen(vm, back)
        Route.History -> HistoryScreen(vm, back)
        Route.ContinueWatching -> ContinueWatchingScreen(vm, back)
        is Route.FolderDetail -> FolderDetailScreen(route.name, vm, back)
        Route.Storage -> StorageScreen(back)
    }
}
