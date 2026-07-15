package jp.unknowntech.mobilemelon.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(
    vm: MelonViewModel,
    onOpenSettings: () -> Unit,
    onReadCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idiHex by vm.idiHex.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (idiHex == null) {
            NotConfigured(onOpenSettings)
        } else {
            when (val b = balance) {
                null, is BalanceUi.Loading -> LoadingColumn("残高を照会中…")

                is BalanceUi.Loaded -> {
                    BalanceCard(b.balance)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { vm.refresh() }) { Text("再読み込み") }
                }

                is BalanceUi.NotRegistered -> {
                    InfoCard("このカード ID は melon に登録されていません。", error = false)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { vm.refresh() }) { Text("再読み込み") }
                }

                is BalanceUi.Error -> {
                    InfoCard("残高を取得できませんでした: ${b.message}", error = true)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.refresh() }) { Text("再試行") }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(Modifier.fillMaxWidth(0.6f))
        Spacer(Modifier.height(20.dp))
        Text(
            "カードをかざして照会",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onReadCard) { Text("カードを読み取る") }
    }
}

@Composable
private fun NotConfigured(onOpenSettings: () -> Unit) {
    Text(
        "カード ID が未登録です。",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "設定でカード ID を登録すると、残高が表示されます。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = onOpenSettings) { Text("カード ID を登録") }
}
