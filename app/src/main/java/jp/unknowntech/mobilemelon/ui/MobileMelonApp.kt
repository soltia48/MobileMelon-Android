package jp.unknowntech.mobilemelon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Screen { Home, Settings, ReadCard }

@Composable
fun MobileMelonApp(
    modifier: Modifier = Modifier,
    vm: MelonViewModel = viewModel(),
) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    Column(modifier = modifier.fillMaxSize()) {
        Header(
            title = when (screen) {
                Screen.Home -> "Mobile Melon"
                Screen.Settings -> "設定"
                Screen.ReadCard -> "カード読み取り"
            },
            onSettings = if (screen == Screen.Home) ({ screen = Screen.Settings }) else null,
            onBack = if (screen != Screen.Home) ({ screen = Screen.Home }) else null,
        )
        when (screen) {
            Screen.Home -> HomeScreen(
                vm = vm,
                onOpenSettings = { screen = Screen.Settings },
                onReadCard = { screen = Screen.ReadCard },
            )

            Screen.Settings -> SettingsScreen(vm = vm, onDone = { screen = Screen.Home })

            Screen.ReadCard -> ReadCardScreen(vm = vm)
        }
    }
}

@Composable
private fun Header(
    title: String,
    onSettings: (() -> Unit)?,
    onBack: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("‹ 戻る") }
        } else {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (onSettings != null) {
            TextButton(onClick = onSettings) { Text("⚙ 設定") }
        } else {
            // Keep the title centered-ish when a back button replaces the title.
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
