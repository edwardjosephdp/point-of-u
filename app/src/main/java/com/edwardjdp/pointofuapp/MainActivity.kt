package com.edwardjdp.pointofuapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.edwardjdp.mongo.database.ImageToDeleteDAO
import com.edwardjdp.mongo.database.ImageToUploadDAO
import com.edwardjdp.mongo.database.entity.ImageToDelete
import com.edwardjdp.mongo.database.entity.ImageToUpload
import com.edwardjdp.pointofuapp.navigation.SetupNavGraph
import com.edwardjdp.ui.theme.PointOfUAppTheme
import com.edwardjdp.util.Constants.APP_ID
import com.edwardjdp.util.Screen
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storageMetadata
import dagger.hilt.android.AndroidEntryPoint
import io.realm.kotlin.mongodb.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var imageToUploadDAO: ImageToUploadDAO

    @Inject
    lateinit var imageToDeleteDAO: ImageToDeleteDAO

    var keepSplashOpen = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        installSplashScreen().setKeepOnScreenCondition {
            keepSplashOpen
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PointOfUAppTheme {
                val navController = rememberNavController()
                SetupNavGraph(
                    startDestination = getStartDestination(),
                    navController = navController,
                    onDataLoaded = {
                        keepSplashOpen = false
                    }
                )
            }
        }

        cleanUpCheck(
            scope = lifecycleScope,
            imageToUploadDAO = imageToUploadDAO,
            imageToDeleteDAO = imageToDeleteDAO,
        )
    }
}

private fun cleanUpCheck(
    scope: CoroutineScope,
    imageToUploadDAO: ImageToUploadDAO,
    imageToDeleteDAO: ImageToDeleteDAO,
) {
    scope.launch(Dispatchers.IO) {
        val result = imageToUploadDAO.getAllImages()
        result.forEach { imageToUpload ->
            retryUploadingImagesToFirebase(
                imageToUpload = imageToUpload,
                onSuccess = {
                    scope.launch(Dispatchers.IO) {
                        imageToUploadDAO.cleanupImage(imageId = imageToUpload.id)
                    }
                }
            )
        }

        val result2 = imageToDeleteDAO.getAllImages()
        result2.forEach { imageToDelete ->
            retryDeletingImagesToFirebase(
                imageToDelete = imageToDelete,
                onSuccess = {
                    scope.launch(Dispatchers.IO) {
                        imageToDeleteDAO.cleanupImage(imageId = imageToDelete.id)
                    }
                }
            )
        }
    }
}

private fun getStartDestination(): String {
    val user = App.create(APP_ID).currentUser
    return if (user != null && user.loggedIn) {
        Screen.Home.route
    } else {
        Screen.Authentication.route
    }
}

fun retryUploadingImagesToFirebase(
    imageToUpload: ImageToUpload,
    onSuccess: () -> Unit
) {
    val storage = FirebaseStorage.getInstance().reference
    storage.child(imageToUpload.remoteImagePath).putFile(
        imageToUpload.imageUri.toUri(),
        storageMetadata {  },
        imageToUpload.sessionUri.toUri()
    ).addOnSuccessListener { onSuccess() }
}

fun retryDeletingImagesToFirebase(
    imageToDelete: ImageToDelete,
    onSuccess: () -> Unit
) {
    val storage = FirebaseStorage.getInstance().reference
    storage.child(imageToDelete.remoteImagePath).delete()
        .addOnSuccessListener { onSuccess() }
}
