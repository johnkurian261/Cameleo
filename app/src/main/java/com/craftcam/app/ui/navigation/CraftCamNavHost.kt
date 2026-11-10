package com.craftcam.app.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.craftcam.app.camera.CameraManager
import com.craftcam.app.camera.IS_VIDEO_FEATURE_PAUSED
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.ui.collection.CollectionScreen
import com.craftcam.app.ui.gallery.GalleryScreen
import com.craftcam.app.ui.settings.SettingsScreen
import com.craftcam.app.ui.viewfinder.ViewfinderScreen

sealed class Screen(val route: String) {
    object Viewfinder : Screen("viewfinder")
    object Collection : Screen("collection")
    object Gallery : Screen("gallery")
    object Settings : Screen("settings")
}

@Composable
fun CraftCamNavHost(
    cameraManager: CameraManager,
    navController: NavHostController = rememberNavController()
) {
    var selectedPreset by remember {
        val initial = if (IS_VIDEO_FEATURE_PAUSED) {
            PresetCatalog.allPresets.first { it.category != CameraCategory.CAMCORDER }
        } else {
            PresetCatalog.allPresets.first()
        }
        mutableStateOf(initial)
    }

    LaunchedEffect(Unit) {
        if (IS_VIDEO_FEATURE_PAUSED && selectedPreset.category == CameraCategory.CAMCORDER) {
            selectedPreset = PresetCatalog.allPresets.first { it.category != CameraCategory.CAMCORDER }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Viewfinder.route
    ) {
        composable(Screen.Viewfinder.route) {
            ViewfinderScreen(
                cameraManager = cameraManager,
                selectedPreset = selectedPreset,
                onPresetChange = { selectedPreset = it },
                onNavigateToCollection = { navController.navigate(Screen.Collection.route) },
                onNavigateToGallery = { navController.navigate(Screen.Gallery.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Collection.route) {
            val uiState by cameraManager.uiState.collectAsState()
            CollectionScreen(
                currentPreset = selectedPreset,
                selectedLens = uiState.shootingState.selectedLens,
                frameEnabled = uiState.tuning.frameBorderEnabled,
                grainEnabled = uiState.tuning.debugStageGrain,
                colorProfileIndex = uiState.tuning.filmColorProfileIndex,
                onSelectPreset = { preset ->
                    selectedPreset = preset
                    cameraManager.setPreset(preset)
                },
                onSelectLens = { lensType ->
                    cameraManager.selectLensAccessory(lensType)
                },
                onToggleFrame = { enabled ->
                    cameraManager.setFrameBorderEnabled(enabled)
                },
                onToggleGrain = { enabled ->
                    cameraManager.setGrainEnabled(enabled)
                },
                onSelectColorProfile = { index ->
                    cameraManager.setFilmColorProfileIndex(index)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Gallery.route) {
            GalleryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

