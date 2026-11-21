package github.franco.chat.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import github.franco.chat.ai.ui.ChatScreen
import github.franco.chat.ai.ui.CustomProviderScreen
import github.franco.chat.ai.ui.ProviderDetailScreen
import github.franco.chat.ai.ui.ProvidersScreen
import github.franco.chat.ai.ui.SettingsScreen
import github.franco.chat.ai.ui.theme.ChatLobiTheme

sealed interface Screen {
    data object Chat : Screen
    data object Settings : Screen
    data object Providers : Screen
    data object CustomProvider : Screen
    data class ProviderDetail(val presetId: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatLobiTheme { AppRoot() }
        }
    }
}

@Composable
private fun AppRoot() {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(androidx.compose.ui.platform.LocalContext.current))
    var screen by remember { mutableStateOf<Screen>(Screen.Chat) }

    BackHandler(enabled = screen != Screen.Chat) {
        screen = when (val current = screen) {
            is Screen.ProviderDetail -> Screen.Providers
            Screen.Providers -> Screen.Settings
            Screen.CustomProvider -> Screen.Providers
            else -> Screen.Chat
        }
    }

    when (val current = screen) {
        Screen.Chat -> ChatScreen(viewModel = viewModel, onOpenSettings = { screen = Screen.Settings })
        Screen.Settings -> SettingsScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.Chat },
            onOpenProviders = { screen = Screen.Providers }
        )
        Screen.Providers -> ProvidersScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.Settings },
            onOpenProvider = { screen = Screen.ProviderDetail(it) },
            onCreateCustom = { screen = Screen.CustomProvider }
        )
        Screen.CustomProvider -> CustomProviderScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.Providers },
            onCreated = { screen = Screen.Providers }
        )
        is Screen.ProviderDetail -> ProviderDetailScreen(
            viewModel = viewModel,
            presetId = current.presetId,
            onBack = { screen = Screen.Providers }
        )
    }
}
