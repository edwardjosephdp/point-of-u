package com.edwardjdp.pointofuapp.presentation.screens.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edwardjdp.pointofuapp.connectivity.ConnectivityObserver
import com.edwardjdp.pointofuapp.connectivity.NetworkConnectivityObserver
import com.edwardjdp.pointofuapp.data.database.ImageToDeleteDAO
import com.edwardjdp.pointofuapp.data.database.entity.ImageToDelete
import com.edwardjdp.pointofuapp.data.repository.Journals
import com.edwardjdp.pointofuapp.data.repository.MongoDB
import com.edwardjdp.pointofuapp.model.RequestState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val connectivity: NetworkConnectivityObserver,
    private val imageToDeleteDAO: ImageToDeleteDAO,
) : ViewModel() {

    private var network by mutableStateOf(ConnectivityObserver.Status.Unavailable)
    var journals: MutableState<Journals> = mutableStateOf(RequestState.Idle)

    init {
        observeAllJournals()
        viewModelScope.launch {
            connectivity.observe().collect { network = it }
        }
    }

    private fun observeAllJournals() {
        viewModelScope.launch {
            MongoDB.getAllJournals().collect { result ->
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
                        val result = MongoDB.deleteAllJournals()
                        when (result) {
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
