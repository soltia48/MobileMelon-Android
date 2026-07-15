package jp.unknowntech.mobilemelon.ui

import android.app.Application
import android.nfc.Tag
import android.nfc.TagLostException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.unknowntech.mobilemelon.data.BalanceResult
import jp.unknowntech.mobilemelon.data.CardId
import jp.unknowntech.mobilemelon.data.CardStore
import jp.unknowntech.mobilemelon.data.MelonApi
import jp.unknowntech.mobilemelon.data.MelonApiException
import jp.unknowntech.mobilemelon.data.SelfBalance
import jp.unknowntech.mobilemelon.nfc.CardFlow
import jp.unknowntech.mobilemelon.nfc.FelicaProtocolException
import jp.unknowntech.mobilemelon.nfc.connectFelica
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** The home screen's balance state, shown once a card ID is registered. */
sealed interface BalanceUi {
    data object Loading : BalanceUi
    data class Loaded(val balance: SelfBalance) : BalanceUi

    /** The saved IDi has no melon account. */
    data object NotRegistered : BalanceUi
    data class Error(val message: String) : BalanceUi
}

/** The card-tap (NFC mutual-authentication) flow's state. */
sealed interface ReadState {
    /** Waiting for a card to be tapped. */
    data object Waiting : ReadState

    /** A card was tapped; relaying the mutual authentication. */
    data object Authenticating : ReadState
    data class Loaded(val balance: SelfBalance) : ReadState
    data object NotRegistered : ReadState

    /** The card cannot be used (e.g. its IDm is randomized). */
    data object Unsupported : ReadState
    data class Error(val message: String) : ReadState
}

class MelonViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CardStore(app)

    /** The saved IDi (16 hex chars), or null when no card ID is registered. */
    private val _idiHex = MutableStateFlow(store.loadIdiHex())
    val idiHex: StateFlow<String?> = _idiHex.asStateFlow()

    /** Balance for the saved card; null while no card ID is registered. */
    private val _balance = MutableStateFlow<BalanceUi?>(null)
    val balance: StateFlow<BalanceUi?> = _balance.asStateFlow()

    /** State of the NFC card-tap lookup. */
    private val _readState = MutableStateFlow<ReadState>(ReadState.Waiting)
    val readState: StateFlow<ReadState> = _readState.asStateFlow()

    /** One tap in flight at a time — Reader Mode re-dispatches the same card. */
    private val handling = AtomicBoolean(false)

    init {
        if (_idiHex.value != null) refresh()
    }

    // ----- saved card ID (typed) -----

    /** The saved card in its canonical display form, or null. */
    fun currentCardId(): String? =
        _idiHex.value?.let { (CardId.parse(it) as? CardId.Result.Ok)?.value?.canonical }

    /**
     * Parse and persist a card ID, then load its balance. Returns the parse
     * result so the settings screen can surface a malformed-input message.
     */
    fun save(input: String): CardId.Result {
        val result = CardId.parse(input)
        if (result is CardId.Result.Ok) {
            store.saveIdiHex(result.value.idiHex)
            _idiHex.value = result.value.idiHex
            refresh()
        }
        return result
    }

    /** Forget the saved card ID. */
    fun clear() {
        store.clear()
        _idiHex.value = null
        _balance.value = null
    }

    /** Re-query the balance for the saved card ID. */
    fun refresh() {
        val hex = _idiHex.value ?: return
        _balance.value = BalanceUi.Loading
        viewModelScope.launch {
            _balance.value = when (val res = MelonApi.selfBalanceByIdi(CardId.SYSTEM_CODE, hex)) {
                is BalanceResult.Success -> BalanceUi.Loaded(res.balance)
                is BalanceResult.NotRegistered -> BalanceUi.NotRegistered
                is BalanceResult.Error -> BalanceUi.Error(res.message)
            }
        }
    }

    // ----- card-present (tap + mutual authentication) -----

    /** Reset the card-tap flow to wait for a fresh tap (ignored mid-relay). */
    fun resetRead() {
        if (!handling.get()) _readState.value = ReadState.Waiting
    }

    /**
     * A FeliCa card was tapped. Connect **on this (reader-callback) thread** so the
     * tag handle stays valid, then relay the server-driven mutual authentication on
     * a background coroutine and show the balance it returns.
     */
    fun onTag(tag: Tag) {
        if (!handling.compareAndSet(false, true)) return
        val nfcF = try {
            connectFelica(tag)
        } catch (e: Exception) {
            handling.set(false)
            _readState.value = classify(e)
            return
        }
        _readState.value = ReadState.Authenticating
        viewModelScope.launch(Dispatchers.IO) {
            val next = try {
                ReadState.Loaded(CardFlow.authenticate(nfcF))
            } catch (e: Exception) {
                classify(e)
            } finally {
                runCatching { nfcF.close() }
                handling.set(false)
            }
            _readState.value = next
        }
    }

    private fun classify(e: Throwable): ReadState = when {
        e is MelonApiException && e.status == 404 -> ReadState.NotRegistered
        e is MelonApiException && (e.status == 422 || e.code == "UNSUPPORTED_CARD") ->
            ReadState.Unsupported

        e is TagLostException ->
            ReadState.Error("カードが離れました。認証が終わるまでかざし続けてください。")

        e is FelicaProtocolException ->
            ReadState.Error("カードの読み取りに失敗しました。もう一度お試しください。")

        e is IOException ->
            ReadState.Error("通信に失敗しました。電波状況をご確認ください。")

        else -> ReadState.Error(e.message ?: "不明なエラーが発生しました。")
    }
}
