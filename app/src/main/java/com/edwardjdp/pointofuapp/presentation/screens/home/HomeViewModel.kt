package com.edwardjdp.pointofuapp.presentation.screens.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edwardjdp.pointofuapp.data.repository.Journals
import com.edwardjdp.pointofuapp.data.repository.MongoDB
import com.edwardjdp.pointofuapp.model.RequestState
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    var journals: MutableState<Journals> = mutableStateOf(RequestState.Idle)

    init {
        observeAllJournals()
    }

    private fun observeAllJournals() {
        viewModelScope.launch {
            MongoDB.getAllJournals().collect { result ->
                journals.value = result
            }
        }
    }
}
