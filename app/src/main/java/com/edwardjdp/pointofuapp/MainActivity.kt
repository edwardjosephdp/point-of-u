package com.edwardjdp.pointofuapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.edwardjdp.pointofuapp.data.database.ImageToDeleteDAO
import com.edwardjdp.pointofuapp.data.database.ImageToUploadDAO
import com.edwardjdp.pointofuapp.navigation.Screen
import com.edwardjdp.pointofuapp.navigation.SetupNavGraph
import com.edwardjdp.pointofuapp.ui.theme.PointOfUAppTheme
import com.edwardjdp.pointofuapp.util.Constants.APP_ID
import com.edwardjdp.pointofuapp.util.retryDeletingImagesToFirebase
import com.edwardjdp.pointofuapp.util.retryUploadingImagesToFirebase
import com.google.firebase.FirebaseApp
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
