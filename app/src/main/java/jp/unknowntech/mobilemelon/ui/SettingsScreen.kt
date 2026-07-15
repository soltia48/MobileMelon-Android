package jp.unknowntech.mobilemelon.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.unknowntech.mobilemelon.data.CardId

@Composable
fun SettingsScreen(
    vm: MelonViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idiHex by vm.idiHex.collectAsStateWithLifecycle()
    val initial = remember { vm.currentCardId() ?: "" }
    var input by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        when (val r = vm.save(input)) {
            is CardId.Result.Invalid -> error = r.message
            is CardId.Result.Ok -> onDone()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            "カード ID",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "モバイル Suica / PASMO などの交通系ICアプリに表示されるカードの識別番号を入力してください。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                error = null
            },
            label = { Text("カード ID") },
            placeholder = { Text("例: JE…") },
            singleLine = true,
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { submit() },
            enabled = input.isNotBlank(),
        ) {
            Text("保存")
        }

        if (idiHex != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    vm.clear()
                    onDone()
                },
            ) {
                Text("カード ID を削除")
            }
        }
    }
}
