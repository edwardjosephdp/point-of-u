package com.edwardjdp.pointofuapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.edwardjdp.pointofuapp.navigation.Screen
import com.edwardjdp.pointofuapp.navigation.SetupNavGraph
import com.edwardjdp.pointofuapp.ui.theme.PointOfUAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            PointOfUAppTheme {
                val navController = rememberNavController()
                SetupNavGraph(
                    startDestination = Screen.Authentication.route,
                    navController = navController
                )

            }
        }
    }
}
