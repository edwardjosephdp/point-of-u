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
import io.realm.kotlin.types.ObjectId
import kotlinx.coroutines.launch

class WriteViewModel(
    private val savedStateHandle: SavedStateHandle
): ViewModel() {

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
                val journal = MongoDB.getSelectedJournal(id = ObjectId.Companion.from(uiState.selectedJournalId!!))

                if (journal is RequestState.Success) {
                    setSelectedJournal(journal = journal.data)
                    setTitle(title = journal.data.title)
                    setDescription(description = journal.data.description)
                    setMood(mood = Mood.valueOf(journal.data.mood))
                }
            }
        }
    }

    fun setSelectedJournal(journal: Journal) {
        uiState = uiState.copy(selectedJournal = journal)
    }

    fun setTitle(title: String) {
        uiState = uiState.copy(title = title)
    }

    fun setDescription(description: String) {
        uiState = uiState.copy(description = description)
    }

    fun setMood(mood: Mood) {
        uiState = uiState.copy(mood = mood)
    }
}

data class UiState(
    val selectedJournalId: String? = null,
    val selectedJournal: Journal? = null,
    val title: String = "",
    val description: String = "",
    val mood: Mood = Mood.Neutral,
)
