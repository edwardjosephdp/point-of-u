package com.edwardjdp.write.navigation

import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.edwardjdp.util.Constants.WRITE_SCREEN_ARGUMENT_KEY
import com.edwardjdp.util.Screen
import com.edwardjdp.util.model.Mood
import com.edwardjdp.write.WriteScreen
import com.edwardjdp.write.WriteViewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.rememberPagerState

@OptIn(ExperimentalPagerApi::class)
fun NavGraphBuilder.writeRoute(
    onBackPressed: () -> Unit,
) {
    composable(
        route = Screen.Write.route,
        arguments = listOf(
            navArgument(name = WRITE_SCREEN_ARGUMENT_KEY) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        )
    ) {
        val context = LocalContext.current
        val viewModel: WriteViewModel = hiltViewModel()
        val uiState = viewModel.uiState
        val pagerState = rememberPagerState()
        val galleryState = viewModel.galleryState
        val pageNumber by remember { derivedStateOf { pagerState.currentPage } }

        WriteScreen(
            uiState = uiState,
            moodName = { Mood.values()[pageNumber].name },
            pagerState = pagerState,
            galleryState = galleryState,
            onTitleChanged = { viewModel.setTitle(title = it) },
            onDescriptionChanged = { viewModel.setDescription(description = it) },
            onDateTimeUpdated = {
                viewModel.updateDateTime(zonedDateTime = it)
            },
            onDeleteConfirmed = {
                viewModel.deleteJournal(
                    onSuccess = {
                        Toast.makeText(context, "Deleted...", Toast.LENGTH_SHORT).show()
                        onBackPressed()
                    },
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        onBackPressed()
                    }
                )
            },
            onBackPressed = onBackPressed,
            onSaveClicked = { journal ->
                viewModel.upsertJournal(
                    journal = journal.apply { mood = Mood.values()[pageNumber].name },
                    onSuccess = { onBackPressed() },
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                )
            },
            onImageSelect = {
                val type = context.contentResolver.getType(it)?.split("/")?.last() ?: "jpg"
                viewModel.addImage(image = it, imageType = type)
            },
            onImageDeleteClicked = { galleryImage ->
                galleryState.removeImage(galleryImage = galleryImage)
            }
        )
    }
}
