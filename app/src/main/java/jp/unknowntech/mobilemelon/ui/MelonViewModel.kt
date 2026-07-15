package jp.unknowntech.mobilemelon.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.unknowntech.mobilemelon.data.BalanceResult
import jp.unknowntech.mobilemelon.data.CardId
import jp.unknowntech.mobilemelon.data.CardStore
import jp.unknowntech.mobilemelon.data.MelonApi
import jp.unknowntech.mobilemelon.data.SelfBalance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The home screen's balance state, shown once a card ID is registered. */
sealed interface BalanceUi {
    data object Loading : BalanceUi
    data class Loaded(val balance: SelfBalance) : BalanceUi

    /** The saved IDi has no melon account. */
    data object NotRegistered : BalanceUi
    data class Error(val message: String) : BalanceUi
}

/** The card-tap (NFC) read flow's state. */
sealed interface ReadState {
    /** Waiting for a card to be tapped. */
    data object Waiting : ReadState

    /** A card was read; querying its balance. */
    data object Querying : ReadState
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

    init {
        if (_idiHex.value != null) refresh()
    }

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
                is BalanceResult.Unsupported -> BalanceUi.Error("このカードは利用できません。")
                is BalanceResult.Error -> BalanceUi.Error(res.message)
            }
        }
    }

    /** Reset the card-tap flow to wait for a fresh tap. */
    fun resetRead() {
        _readState.value = ReadState.Waiting
    }

    /**
     * A FeliCa card was tapped: look up its balance by IDm. Called from the NFC
     * reader-mode callback, which runs off the main thread — [MutableStateFlow]
     * and `viewModelScope.launch` are both safe there.
     */
    fun onCardRead(systemCode: Int, idmHex: String) {
        if (_readState.value is ReadState.Querying) return
        _readState.value = ReadState.Querying
        viewModelScope.launch {
            _readState.value = when (val res = MelonApi.selfBalanceByIdm(systemCode, idmHex)) {
                is BalanceResult.Success -> ReadState.Loaded(res.balance)
                is BalanceResult.NotRegistered -> ReadState.NotRegistered
                is BalanceResult.Unsupported -> ReadState.Unsupported
                is BalanceResult.Error -> ReadState.Error(res.message)
            }
        }
    }
}
