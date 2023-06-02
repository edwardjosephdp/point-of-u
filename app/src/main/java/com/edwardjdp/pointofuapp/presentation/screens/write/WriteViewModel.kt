package com.edwardjdp.pointofuapp.presentation.screens.write

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edwardjdp.pointofuapp.data.repository.MongoDB
import com.edwardjdp.pointofuapp.model.Journal
import com.edwardjdp.pointofuapp.model.Mood
import com.edwardjdp.pointofuapp.util.Constants.WRITE_SCREEN_ARGUMENT_KEY
import com.edwardjdp.pointofuapp.util.RequestState
import com.edwardjdp.pointofuapp.util.toRealmInstant
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

class WriteViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    var uiState by mutableStateOf(UiState())
        private set

    init {
        getJournalIdArgument()
        fetchSelectedJournal()
    }

    private fun getJournalIdArgument() {
        uiState = uiState.copy(
            selectedJournalId = savedStateHandle.get<String>(
                key = WRITE_SCREEN_ARGUMENT_KEY
            )
        )
    }

    private fun fetchSelectedJournal() {
        if (uiState.selectedJournalId != null) {
            viewModelScope.launch {
                MongoDB
                    .getSelectedJournal(id = ObjectId.Companion.from(uiState.selectedJournalId!!))
                    .catch {
                        emit(RequestState.Error(Exception("Journal is already deleted.")))
                    }
                    .collect { journal ->
                        if (journal is RequestState.Success) {
                            setSelectedJournal(journal = journal.data)
                            setTitle(title = journal.data.title)
                            setDescription(description = journal.data.description)
                            setMood(mood = Mood.valueOf(journal.data.mood))
                        }
                    }
            }
        }
    }

    fun setTitle(title: String) {
        uiState = uiState.copy(title = title)
    }

    fun setDescription(description: String) {
        uiState = uiState.copy(description = description)
    }

    private fun setMood(mood: Mood) {
        uiState = uiState.copy(mood = mood)
    }

    fun updateDateTime(zonedDateTime: ZonedDateTime) {
        uiState = uiState.copy(updatedDateTime = zonedDateTime.toInstant().toRealmInstant())
    }

    private fun setSelectedJournal(journal: Journal) {
        uiState = uiState.copy(selectedJournal = journal)
    }

    fun upsertJournal(
        journal: Journal,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (uiState.selectedJournalId != null) {
                updateJournal(
                    journal,
                    onSuccess,
                    onError
                )
            } else {
                insertJournal(
                    journal,
                    onSuccess,
                    onError
                )
            }
        }
    }

    private suspend fun insertJournal(
        journal: Journal,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val result = MongoDB.insertJournal(journal = journal.apply {
            if (uiState.updatedDateTime != null) {
                date = uiState.updatedDateTime!!
            }
        })
        when (result) {
            is RequestState.Error -> withContext(Dispatchers.Main) { onError(result.error.message.toString()) }
            is RequestState.Success -> withContext(Dispatchers.Main) { onSuccess() }
            else -> {
                /* no-op */
            }
        }
    }

    private suspend fun updateJournal(
        journal: Journal,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val result = MongoDB.updateJournal(
            journal = journal.apply {
                _id = ObjectId.Companion.from(uiState.selectedJournalId!!)
                date = if (uiState.updatedDateTime != null) {
                    uiState.updatedDateTime!!
                } else {
                    uiState.selectedJournal!!.date
                }
            }
        )
        when (result) {
            is RequestState.Error -> withContext(Dispatchers.Main) { onError(result.error.message.toString()) }
            is RequestState.Success -> withContext(Dispatchers.Main) { onSuccess() }
            else -> {
                /* no-op */
            }
        }
    }

    fun deleteJournal(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (uiState.selectedJournalId != null) {
                when (val result = MongoDB.deleteJournal(journalId = ObjectId.from(uiState.selectedJournalId!!))) {
                    is RequestState.Error -> withContext(Dispatchers.Main) { onError(result.error.message.toString()) }
                    is RequestState.Success -> withContext(Dispatchers.Main) { onSuccess() }
                    else -> {
                        /* no-op */
                    }
                }
            }
        }
    }
}

data class UiState(
    val selectedJournalId: String? = null,
    val selectedJournal: Journal? = null,
    val title: String = "",
    val description: String = "",
    val mood: Mood = Mood.Neutral,
    val updatedDateTime: RealmInstant? = null
)
