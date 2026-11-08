package com.chitfund.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chitfund.app.ui.ChitScreen
import com.chitfund.app.ui.HelpScreen
import com.chitfund.app.ui.HomeScreen
import com.chitfund.app.ui.LockScreen
import com.chitfund.app.ui.NewChitScreen
import com.chitfund.app.ui.PinSetupScreen
import com.chitfund.app.ui.ProfileScreen
import com.chitfund.app.ui.QuestionScreen
import com.chitfund.app.ui.SettingsScreen

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0F5132),
                    onPrimary = Color.White,
                    secondary = Color(0xFF198754),
                    background = Color(0xFFDCEBE1),
                    surface = Color(0xFFEAF3ED),
                    surfaceVariant = Color(0xFFC6DCCE),
                    onSurfaceVariant = Color(0xFF34423A),
                    surfaceContainerHighest = Color(0xFFEAF3ED),
                    surfaceContainerHigh = Color(0xFFEAF3ED),
                    surfaceContainer = Color(0xFFE4EFE8),
                    surfaceContainerLow = Color(0xFFE4EFE8),
                    secondaryContainer = Color(0xFFC6DCCE),
                    onSecondaryContainer = Color(0xFF0F5132)
                )
            ) {
                val nav = rememberNavController()
                val vm: ChitViewModel = viewModel()
                val profileVm: ProfileViewModel = viewModel()
                val securityVm: SecurityViewModel = viewModel()
                val locked by securityVm.locked.collectAsState()

                if (locked) {
                    LockScreen(securityVm) { securityVm.unlock() }
                } else {
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                vm,
                                profileVm,
                                onOpen = { nav.navigate("chit/$it") },
                                onNew = { nav.navigate("new/$it") },
                                onNavigate = { nav.navigate(it) }
                            )
                        }
                        composable("profile") { ProfileScreen(profileVm) { nav.popBackStack() } }
                        composable("settings") {
                            SettingsScreen(
                                securityVm,
                                onBack = { nav.popBackStack() },
                                onSetupPin = { nav.navigate("setpin") },
                                onChangePin = { nav.navigate("changepin") },
                                onChangeQuestion = { nav.navigate("question") }
                            )
                        }
                        composable("setpin") {
                            PinSetupScreen(securityVm, changing = false,
                                onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                        }
                        composable("changepin") {
                            PinSetupScreen(securityVm, changing = true,
                                onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                        }
                        composable("question") {
                            QuestionScreen(securityVm,
                                onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                        }
                        composable("help") { HelpScreen { nav.popBackStack() } }
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
}
