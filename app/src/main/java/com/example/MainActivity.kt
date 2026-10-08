package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.moduli.ModuloCaricoScreen
import com.example.moduli.ModuloDispensaScreen
import com.example.moduli.ModuloModificaScreen
import com.example.moduli.ModuloScaricoScreen
import com.example.moduli.ModuloSpesaScreen
import com.example.moduli.ModuloSyncScreen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Settings
import com.example.ui.theme.DispensaSmartTheme
import com.example.ui.theme.ForestGreenPrimary
import com.example.viewmodel.DispensaViewModel

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.delay
import android.util.Log

import android.os.Build
import android.Manifest
import android.content.Intent
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    var pendingSharedKeepText by mutableStateOf<Pair<String, String?>?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startBackgroundService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSendIntent(intent)
        
        // Request POST_NOTIFICATIONS on Android 13+ and start service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startBackgroundService()
            }
        } else {
            startBackgroundService()
        }

        enableEdgeToEdge()
        setContent {
            DispensaSmartTheme {
                DispensaSmartApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!text.isNullOrBlank()) {
                pendingSharedKeepText = Pair(text, subject)
            }
        }
    }

    private fun startBackgroundService() {
        val serviceIntent = Intent(this, com.example.servizi.AppForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}

sealed class NavItem(val route: String, val title: String, val icon: ImageVector) {
    object Dispensa : NavItem("dispensa", "Dispensa", Icons.Default.Kitchen)
    object Carico : NavItem("carico", "Carico", Icons.Default.AddCircleOutline)
    object Scarico : NavItem("scarico", "Scarico", Icons.Default.Fastfood)
    object Spesa : NavItem("spesa", "Spesa", Icons.Default.ShoppingCart)
    object Impostazioni : NavItem("impostazioni", "Impostazioni", Icons.Default.Settings)
    object Modifica : NavItem("modifica", "Modifica", Icons.Default.Kitchen)
}

@Composable
fun DispensaSmartApp() {
    val navController = rememberNavController()
    val viewModel: DispensaViewModel = viewModel()

    val navItems = listOf(
        NavItem.Dispensa,
        NavItem.Carico,
        NavItem.Scarico,
        NavItem.Spesa,
        NavItem.Impostazioni
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val prodottiSpesa by viewModel.prodottiSpesa.collectAsStateWithLifecycle()
    val spesaCount = prodottiSpesa.size
    
    val context = LocalContext.current
    val activity = context as? MainActivity
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(activity?.pendingSharedKeepText) {
        activity?.pendingSharedKeepText?.let { (text, subject) ->
            viewModel.importaProdottiDaKeepTesto(text, subject)
            navController.navigate(NavItem.Spesa.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
            }
            activity.pendingSharedKeepText = null
        }
    }

    LaunchedEffect(lastInteractionTime) {
        delay(60_000L) // Esempio: 60 secondi di timeout (collegabile poi alle preferenze condivise)
        Log.d("CantinaTimeout", "Timeout di inattività raggiunto, blocco lo schermo.")
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val compName = ComponentName(context, com.example.servizi.CantinaDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(compName)) {
                dpm.lockNow()
            } else {
                Log.w("CantinaTimeout", "Permessi di Device Admin non attivi per spegnere lo schermo.")
            }
        } catch (e: Exception) {
            Log.e("CantinaTimeout", "Errore durante il blocco", e)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(pass = PointerEventPass.Initial)
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
            },
        bottomBar = {
            // Hide bottom bar on detail edit screen
            if (currentRoute != NavItem.Modifica.route) {
                NavigationBar(
                    containerColor = com.example.ui.theme.SandSurface,
                    contentColor = ForestGreenPrimary,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("main_navigation_bar")
                ) {
                    navItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (item.route == NavItem.Spesa.route && spesaCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge { Text("$spesaCount") }
                                        }
                                    ) {
                                        Icon(imageVector = item.icon, contentDescription = item.title)
                                    }
                                } else {
                                    Icon(imageVector = item.icon, contentDescription = item.title)
                                }
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = com.example.ui.theme.SageGreenContainer,
                                selectedIconColor = ForestGreenPrimary,
                                selectedTextColor = ForestGreenPrimary
                            ),
                            modifier = Modifier.testTag("nav_item_${item.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavItem.Dispensa.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavItem.Dispensa.route) {
                ModuloDispensaScreen(
                    viewModel = viewModel,
                    onNavigateToCarico = { navController.navigate(NavItem.Carico.route) },
                    onNavigateToModifica = { navController.navigate(NavItem.Modifica.route) }
                )
            }

            composable(NavItem.Carico.route) {
                ModuloCaricoScreen(
                    viewModel = viewModel,
                    onSaveComplete = { navController.navigate(NavItem.Dispensa.route) }
                )
            }

            composable(NavItem.Scarico.route) {
                ModuloScaricoScreen(
                    viewModel = viewModel
                )
            }

            composable(NavItem.Spesa.route) {
                ModuloSpesaScreen(
                    viewModel = viewModel
                )
            }

            composable(NavItem.Impostazioni.route) {
                com.example.moduli.ModuloSyncScreen(
                    viewModel = viewModel
                )
            }

            composable(NavItem.Modifica.route) {
                ModuloModificaScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
