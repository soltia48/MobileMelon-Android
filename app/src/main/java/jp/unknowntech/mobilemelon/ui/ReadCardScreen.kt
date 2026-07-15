package jp.unknowntech.mobilemelon.ui

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.unknowntech.mobilemelon.nfc.READER_FLAGS
import jp.unknowntech.mobilemelon.nfc.READER_PRESENCE_CHECK_DELAY_MS
import jp.unknowntech.mobilemelon.nfc.findActivity

@Composable
fun ReadCardScreen(
    vm: MelonViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val readState by vm.readState.collectAsStateWithLifecycle()

    // Fresh tap each time the screen is opened.
    LaunchedEffect(Unit) { vm.resetRead() }

    // Reader mode is bound to the Activity; enable it only while this screen shows.
    DisposableEffect(activity, nfcAdapter) {
        if (activity != null && nfcAdapter != null && nfcAdapter.isEnabled) {
            val extras = Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, READER_PRESENCE_CHECK_DELAY_MS)
            }
            val callback = NfcAdapter.ReaderCallback { tag -> vm.onTag(tag) }
            nfcAdapter.enableReaderMode(activity, callback, READER_FLAGS, extras)
            onDispose { runCatching { nfcAdapter.disableReaderMode(activity) } }
        } else {
            onDispose {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            nfcAdapter == null ->
                InfoCard("この端末は NFC に対応していません。", error = true)

            !nfcAdapter.isEnabled -> {
                InfoCard("NFC がオフになっています。設定でオンにしてください。", error = true)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                }) { Text("NFC 設定を開く") }
            }

            else -> ReadContent(readState, onReset = { vm.resetRead() })
        }
    }
}

@Composable
private fun ReadContent(state: ReadState, onReset: () -> Unit) {
    when (state) {
        is ReadState.Waiting -> Prompt(
            "カードを端末の背面にかざし、\n認証が終わるまで離さないでください",
        )

        is ReadState.Authenticating -> LoadingColumn("認証中… カードを離さないでください")

        is ReadState.Loaded -> {
            BalanceCard(state.balance)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onReset) { Text("別のカードを読み取る") }
        }

        is ReadState.NotRegistered -> ResultWithRetry(
            "このカードは melon に登録されていません。",
            error = false,
            onReset = onReset,
        )

        is ReadState.Unsupported -> ResultWithRetry(
            "このカードは利用できません（ID が乱数化されています）。",
            error = true,
            onReset = onReset,
        )

        is ReadState.Error -> ResultWithRetry(
            state.message,
            error = true,
            onReset = onReset,
        )
    }
}

@Composable
private fun Prompt(text: String) {
    Text(
        "📱",
        style = MaterialTheme.typography.displayMedium,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ResultWithRetry(message: String, error: Boolean, onReset: () -> Unit) {
    InfoCard(message, error = error)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onReset) { Text("もう一度読み取る") }
}
