package com.chitfund.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chitfund.app.ui.ChitScreen
import com.chitfund.app.ui.HomeScreen
import com.chitfund.app.ui.NewChitScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0F5132),
                    onPrimary = Color.White,
                    secondary = Color(0xFF198754),
                    background = Color(0xFFF3F6F4),
                    surface = Color.White,
                    surfaceVariant = Color(0xFFDCE6E0),
                    onSurfaceVariant = Color(0xFF34423A),
                    surfaceContainerHighest = Color.White
                )
            ) {
                val nav = rememberNavController()
                val vm: ChitViewModel = viewModel()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            vm,
                            onOpen = { nav.navigate("chit/$it") },
                            onNew = { nav.navigate("new/$it") }
                        )
                    }
                    composable("new/{role}") {
                        NewChitScreen(vm, it.arguments?.getString("role") ?: "ORGANISER") {
                            nav.popBackStack()
                        }
                    }
                    composable("chit/{id}") {
                        val id = it.arguments?.getString("id")?.toLong() ?: 0L
                        ChitScreen(vm, id) { nav.popBackStack() }
                    }
                }
            }
        }
    }
}
