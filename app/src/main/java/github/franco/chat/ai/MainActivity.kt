package github.franco.chat.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import github.franco.chat.ai.ui.ChatScreen
import github.franco.chat.ai.ui.SettingsScreen
import github.franco.chat.ai.ui.theme.ChatLobiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatLobiTheme {
                val viewModel: ChatViewModel =
                    viewModel(factory = ChatViewModel.factory(applicationContext))
                var showSettings by remember { mutableStateOf(false) }

                if (showSettings) {
                    SettingsScreen(viewModel = viewModel, onBack = { showSettings = false })
                } else {
                    ChatScreen(viewModel = viewModel, onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}
