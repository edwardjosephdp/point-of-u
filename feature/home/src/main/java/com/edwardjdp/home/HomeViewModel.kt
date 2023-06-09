package com.edwardjdp.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edwardjdp.mongo.database.ImageToDeleteDAO
import com.edwardjdp.mongo.database.entity.ImageToDelete
import com.edwardjdp.mongo.repository.Journals
import com.edwardjdp.mongo.repository.MongoDB
import com.edwardjdp.util.connectivity.ConnectivityObserver
import com.edwardjdp.util.connectivity.NetworkConnectivityObserver
import com.edwardjdp.util.model.RequestState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val connectivity: NetworkConnectivityObserver,
    private val imageToDeleteDAO: ImageToDeleteDAO,
) : ViewModel() {

    private lateinit var allJournalsJob: Job
    private lateinit var filteredJournalsJob: Job

    private var network by mutableStateOf(ConnectivityObserver.Status.Unavailable)
    var journals: MutableState<Journals> = mutableStateOf(RequestState.Idle)
    var dateIsSelected by mutableStateOf(false)
        private set

    init {
        getJournals()
        viewModelScope.launch {
            connectivity.observe().collect { network = it }
        }
    }

    fun getJournals(zonedDateTime: ZonedDateTime? = null) {
        dateIsSelected = zonedDateTime != null
        journals.value = RequestState.Loading
        if (dateIsSelected && zonedDateTime != null) {
            observeFilteredJournals(zonedDateTime = zonedDateTime)
        } else {
            observeAllJournals()
        }
    }


    private fun observeAllJournals() {
        allJournalsJob = viewModelScope.launch {
            if (::filteredJournalsJob.isInitialized) {
                filteredJournalsJob.cancelAndJoin()
            }
            MongoDB.getAllJournals().collect { result ->
                journals.value = result
            }
        }
    }

    private fun observeFilteredJournals(zonedDateTime: ZonedDateTime) {
        filteredJournalsJob = viewModelScope.launch {
            if (::allJournalsJob.isInitialized) {
                allJournalsJob.cancelAndJoin()
            }
            MongoDB.getFilteredJournals(zonedDateTime = zonedDateTime).collect { result ->
                journals.value = result
            }
        }
    }

    fun deleteAllJournals(
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        if (network == ConnectivityObserver.Status.Available) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            val imagesDirectory = "images/$userId"
            val storage = FirebaseStorage.getInstance().reference
            storage.child(imagesDirectory)
                .listAll()
                .addOnSuccessListener {
                    it.items.forEach { reference ->
                        val imagePath = "images/$userId/${reference.name}"
                        storage.child(imagePath).delete()
                            .addOnFailureListener {
                                viewModelScope.launch(Dispatchers.IO) {
                                    imageToDeleteDAO.addImageToDelete(
                                        imageToDelete = ImageToDelete(
                                            remoteImagePath = imagePath
                                        )
                                    )
                                }
                            }
                    }

                    viewModelScope.launch(Dispatchers.IO) {
                        when (val result = MongoDB.deleteAllJournals()) {
                            is RequestState.Error -> withContext(Dispatchers.Main) { onError(result.error) }
                            is RequestState.Success -> withContext(Dispatchers.Main) { onSuccess() }
                            else -> {
                                /* no-op */
                            }
                        }
                    }
                }
                .addOnFailureListener { onError(it) }
        } else {
            onError(Exception("No Internet Connection."))
        }
    }
}
