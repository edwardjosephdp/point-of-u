package com.edwardjdp.write

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edwardjdp.mongo.database.ImageToDeleteDAO
import com.edwardjdp.mongo.database.ImageToUploadDAO
import com.edwardjdp.mongo.database.entity.ImageToDelete
import com.edwardjdp.mongo.database.entity.ImageToUpload
import com.edwardjdp.mongo.repository.MongoDB
import com.edwardjdp.util.model.Journal
import com.edwardjdp.util.model.Mood
import com.edwardjdp.util.model.RequestState
import com.edwardjdp.ui.GalleryImage
import com.edwardjdp.ui.GalleryState
import com.edwardjdp.util.Constants.WRITE_SCREEN_ARGUMENT_KEY
import com.edwardjdp.util.fetchImagesFromFirebase
import com.edwardjdp.util.toRealmInstant
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mongodb.kbson.ObjectId
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
internal class WriteViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imageToUploadDAO: ImageToUploadDAO,
    private val imageToDeleteDAO: ImageToDeleteDAO,
) : ViewModel() {

    val galleryState = GalleryState()
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
                    .getSelectedJournal(id = ObjectId.invoke(uiState.selectedJournalId!!))
                    .catch {
                        emit(RequestState.Error(Exception("Journal is already deleted.")))
                    }
                    .collect { journal ->
                        if (journal is RequestState.Success) {
                            setSelectedJournal(journal = journal.data)
                            setTitle(title = journal.data.title)
                            setDescription(description = journal.data.description)
                            setMood(mood = Mood.valueOf(journal.data.mood))

                            fetchImagesFromFirebase(
                                remoteImagePaths = journal.data.images,
                                onImageDownload = { downloadedImage ->
                                    galleryState.addImage(
                                        GalleryImage(
                                            imageUri = downloadedImage,
                                            remoteImagePath = extractImagePath(
                                                remotePath = downloadedImage.toString()
                                            )
                                        )
                                    )
                                }
                            )
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

    @SuppressLint("NewApi")
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
                updateJournal(journal = journal, onSuccess = onSuccess, onError = onError)
            } else {
                insertJournal(journal = journal, onSuccess = onSuccess, onError = onError)
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
            is RequestState.Success -> {
                uploadImagesToFirebase()
                withContext(Dispatchers.Main) { onSuccess() }
            }

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
                _id = ObjectId.invoke(uiState.selectedJournalId!!)
                date = if (uiState.updatedDateTime != null) {
                    uiState.updatedDateTime!!
                } else {
                    uiState.selectedJournal!!.date
                }
            }
        )
        when (result) {
            is RequestState.Error -> withContext(Dispatchers.Main) { onError(result.error.message.toString()) }
            is RequestState.Success -> {
                uploadImagesToFirebase()
                deleteImagesToFirebase()
                withContext(Dispatchers.Main) { onSuccess() }
            }

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
                when (val result =
                    MongoDB.deleteJournal(journalId = ObjectId.invoke(uiState.selectedJournalId!!))) {
                    is RequestState.Error -> withContext(Dispatchers.Main) { onError(result.error.message.toString()) }
                    is RequestState.Success -> withContext(Dispatchers.Main) {
                        uiState.selectedJournal?.let { deleteImagesToFirebase(images = it.images) }
                        onSuccess()
                    }

                    else -> {
                        /* no-op */
                    }
                }
            }
        }
    }

    fun addImage(image: Uri, imageType: String) {
        val remoteImagePath = "images/${FirebaseAuth.getInstance().currentUser?.uid}/" +
                "${image.lastPathSegment}-${System.currentTimeMillis()}.$imageType"
        galleryState.addImage(
            GalleryImage(
                imageUri = image,
                remoteImagePath = remoteImagePath,
            )
        )
    }

    private fun uploadImagesToFirebase() {
        val storage = FirebaseStorage.getInstance().reference
        galleryState.images.forEach { galleryImage ->
            val imagePath = storage.child(galleryImage.remoteImagePath)
            imagePath.putFile(galleryImage.imageUri)
                .addOnProgressListener {
                    val sessionUri = it.uploadSessionUri
                    if (sessionUri != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            imageToUploadDAO.addImageToUpload(
                                ImageToUpload(
                                    remoteImagePath = galleryImage.remoteImagePath,
                                    imageUri = galleryImage.imageUri.toString(),
                                    sessionUri = sessionUri.toString()
                                )
                            )
                        }
                    }
                }
        }
    }

    private fun deleteImagesToFirebase(images: List<String>? = null) {
        val storage = FirebaseStorage.getInstance().reference
        if (images != null) {
            images.forEach { remotePath ->
                storage.child(remotePath).delete()
                    .addOnFailureListener {
                        viewModelScope.launch(Dispatchers.IO) {
                            imageToDeleteDAO.addImageToDelete(
                                ImageToDelete(
                                    remoteImagePath = remotePath
                                )
                            )
                        }
                    }
            }
        } else {
            galleryState.imagesToBeDeleted.map { it.remoteImagePath }.forEach { remotePath ->
                storage.child(remotePath).delete()
                    .addOnFailureListener {
                        viewModelScope.launch(Dispatchers.IO) {
                            imageToDeleteDAO.addImageToDelete(
                                ImageToDelete(
                                    remoteImagePath = remotePath
                                )
                            )
                        }
                    }
            }
        }
    }

    private fun extractImagePath(remotePath: String): String {
        val chunks = remotePath.split("%2F")
        val imageName = chunks[2].split("?").first()
        return "images/${Firebase.auth.currentUser?.uid}/$imageName"
    }
}

internal data class UiState(
    val selectedJournalId: String? = null,
    val selectedJournal: Journal? = null,
    val title: String = "",
    val description: String = "",
    val mood: Mood = Mood.Neutral,
    val updatedDateTime: RealmInstant? = null
)
