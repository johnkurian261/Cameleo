package com.craftcam.app.ui.collection

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.R
import com.craftcam.app.camera.IS_VIDEO_FEATURE_PAUSED
import com.craftcam.app.camera.profile.CameraProfileCatalog
import com.craftcam.app.camera.profile.LensProfile
import com.craftcam.app.camera.profile.LensType
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.ui.theme.*

data class ModelShelfItem(
    val label: String,
    val iconResName: String,
    val presetId: String
)

@Composable
fun CollectionScreen(
    currentPreset: CameraPreset,
    selectedLens: LensProfile = LensProfile.NORMAL,
    onSelectPreset: (CameraPreset) -> Unit,
    onSelectLens: (LensType) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<CameraCategory?>(null) }
    val view = LocalView.current
    val context = LocalContext.current

    val displayedPresets = remember(selectedCategory) {
        val base = if (IS_VIDEO_FEATURE_PAUSED) {
            PresetCatalog.allPresets.filter { it.category != CameraCategory.CAMCORDER }
        } else {
            PresetCatalog.allPresets
        }
        if (selectedCategory == null) {
            base
        } else {
            base.filter { it.category == selectedCategory }
        }
    }

    val visibleCategories = remember {
        if (IS_VIDEO_FEATURE_PAUSED) {
            CameraCategory.values().filter { it != CameraCategory.CAMCORDER }
        } else {
            CameraCategory.values().toList()
        }
    }

    // 3D Model Shelves — dynamically mapped to all Cameleo presets
    val videoModels = remember {
        PresetCatalog.allPresets
            .filter { it.category == CameraCategory.CAMCORDER }
            .map { preset ->
                val shortLabel = when (preset.id) {
                    "cam_retro_vhs"     -> "Bessa"
                    "cam_analog_video"  -> "QuickSnap"
                    "cam_90s_camcorder" -> "Lomo"
                    else                -> preset.name
                }
                ModelShelfItem(
                    label = shortLabel,
                    iconResName = preset.iconResName,
                    presetId = preset.id
                )
            }
    }

    // Complete Photo Camera models shelf — includes EVERY photo camera in CAMELEO (16 cameras)
    val photoModels = remember {
        val photoPresets = PresetCatalog.allPresets.filter { it.category != CameraCategory.CAMCORDER }
        photoPresets.map { preset ->
            val shortLabel = when (preset.id) {
                "zero"                 -> "Zero"
                "analog_35mm_classic", "d_classic", "dclassic", "d classic", "classic_35mm" -> "D Classic"
                "analog_fxn_r"         -> "FXNR"
                "analog_fisheye_f"     -> "Fisheye"
                "analog_warm_film"     -> "Warm Film"
                "analog_soft_film"     -> "CT2F"
                "analog_vintage_color" -> "D Exp"
                "analog_cinema_film", "d3d", "d_3d" -> "D 3D"
                "ccd_r", "ccdr", "ccd r", "ccd-r", "ccd_classic" -> "CCD R"
                "ccd_flash", "ccd flash", "d_funs", "dfuns" -> "CCD Flash"
                "ccd_y2k_digital", "classic_u", "classicu" -> "Classic U"
                "ccd_retro_compact", "dqs" -> "DQS"
                "disp_classic"         -> "Golf"
                "disp_flash"           -> "CPM35"
                "disp_warm"            -> "135SR"
                "disp_night"           -> "D Half"
                "inst_vintage"         -> "Polaroid"
                else                   -> preset.name
            }
            ModelShelfItem(
                label = shortLabel,
                iconResName = preset.iconResName,
                presetId = preset.id
            )
        }
    }

    val photoShelfState = rememberLazyListState()

    // Smoothly scroll shelf to active camera on launch or selection change
    LaunchedEffect(currentPreset.id, photoModels) {
        val activeIdx = photoModels.indexOfFirst { it.presetId == currentPreset.id }
        if (activeIdx >= 0) {
            photoShelfState.animateScrollToItem(activeIdx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack)
    ) {
        // Cameleo Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Chameleon Cameleo Logo
                val logoResId = remember {
                    context.resources.getIdentifier("cameleo_logo", "drawable", context.packageName)
                }
                if (logoResId != 0) {
                    Image(
                        painter = painterResource(id = logoResId),
                        contentDescription = "Cameleo Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column {
                    Text(
                        text = "CAMELEO",
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                    Text(
                        text = "EVERY MOOD. EVERY LENS.",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = TextMuted
                    )
                }
            }

            // Close / Back button
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ==================== 1. ▷ VIDEO SHELF (PAUSED) ====================
            if (!IS_VIDEO_FEATURE_PAUSED) {
                item {
                    ShelfSectionHeader(title = "▷ Video")
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(videoModels) { item ->
                            val isSelected = currentPreset.id == item.presetId
                            Camera3DModelTile(
                                item = item,
                                isSelected = isSelected,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    PresetCatalog.getPresetById(item.presetId)?.let { onSelectPreset(it) }
                                    onNavigateBack()
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // ==================== 2. 📷 PHOTO SHELF ====================
            item {
                ShelfSectionHeader(title = "📷 Photo Cameras (${photoModels.size})")
                LazyRow(
                    state = photoShelfState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(photoModels, key = { it.presetId }) { item ->
                        val isSelected = currentPreset.id == item.presetId
                        Camera3DModelTile(
                            item = item,
                            isSelected = isSelected,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                PresetCatalog.getPresetById(item.presetId)?.let { onSelectPreset(it) }
                                onNavigateBack()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ==================== 3. CATALOG FILTER TABS ====================
            item {
                ShelfSectionHeader(
                    title = if (IS_VIDEO_FEATURE_PAUSED)
                        "🎞 Film & Digital Emulsions (${displayedPresets.size})"
                    else
                        "🎞 All Camera Emulsions (${PresetCatalog.allPresets.size})"
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    item {
                        CategoryFilterPill(
                            title = "ALL",
                            isSelected = selectedCategory == null,
                            onClick = {
                                selectedCategory = null
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        )
                    }
                    items(visibleCategories) { category ->
                        CategoryFilterPill(
                            title = category.displayName.uppercase(),
                            isSelected = selectedCategory == category,
                            onClick = {
                                selectedCategory = category
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ==================== 5. DETAILED CAMERA CARDS ====================
            items(displayedPresets, key = { it.id }) { preset ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    CameraCard(
                        preset = preset,
                        isSelected = preset.id == currentPreset.id,
                        selectedLens = selectedLens,
                        onSelect = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSelectPreset(preset)
                            onNavigateBack()
                        },
                        onSelectPresetDirect = { p ->
                            onSelectPreset(p)
                        },
                        onSelectLens = onSelectLens
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = VintageAmberGlow
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.8.dp)
                .background(DividerSubtle)
        )
    }
}

@Composable
private fun Camera3DModelTile(
    item: ModelShelfItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val iconResId = remember(item.iconResName) {
        context.resources.getIdentifier(item.iconResName, "drawable", context.packageName)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        if (iconResId != 0) {
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = item.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(width = 82.dp, height = 58.dp)
                    .padding(bottom = 6.dp)
            )
        }

        // Tactile Capsule Label
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) Color.White else SurfaceCard)
                .border(
                    width = 1.dp,
                    color = if (isSelected) Color.White else DividerSubtle,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                text = item.label,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.Black else TextSecondary
            )
        }
    }
}

@Composable
private fun CategoryFilterPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) VintageAmber else SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) CameraBlack else TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CameraCard(
    preset: CameraPreset,
    isSelected: Boolean,
    selectedLens: LensProfile,
    onSelect: () -> Unit,
    onSelectPresetDirect: (CameraPreset) -> Unit,
    onSelectLens: (LensType) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val iconResId = remember(preset.iconResName) {
        context.resources.getIdentifier(preset.iconResName, "drawable", context.packageName)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) VintageAmber else DividerSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onSelect)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (iconResId != 0) {
                        Image(
                            painter = painterResource(id = iconResId),
                            contentDescription = preset.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(52.dp)
                                .padding(end = 10.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceElevated)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = preset.year,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = VintageAmber
                                )
                            }
                        }

                        Text(
                            text = preset.subtitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = VintageAmberGlow
                        )
                    }
                }

                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = VintageAmber,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp,
                color = TextSecondary
            )

            // Sample photograph preview demonstrating the filter look
            val sampleResId = remember(preset.sampleResName) {
                context.resources.getIdentifier(preset.sampleResName, "drawable", context.packageName)
            }
            if (sampleResId != 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Image(
                    painter = painterResource(id = sampleResId),
                    contentDescription = preset.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // ── Optical Lens Accessory System ───────────────────────────────────
            val profile = remember(preset.id) { CameraProfileCatalog.getProfileForPreset(preset) }
            val supportedLenses = profile.supportedLenses
            if (supportedLenses.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OPTICAL LENSES",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = VintageAmber,
                        letterSpacing = 1.sp
                    )
                    if (isSelected) {
                        Text(
                            text = "MOUNTED: ${selectedLens.name.uppercase()}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = VintageAmberGlow
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(supportedLenses) { lensType ->
                        val isMounted = isSelected && selectedLens.type == lensType
                        val label = lensType.displayName

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isMounted) VintageAmber else SurfaceElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (isMounted) VintageAmberGlow else DividerSubtle,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    if (!isSelected) {
                                        onSelectPresetDirect(preset)
                                    }
                                    onSelectLens(lensType)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = lensType.iconRes),
                                    contentDescription = label,
                                    modifier = Modifier.size(24.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMounted) CameraBlack else TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Optical & Chemical Emulsion Specification Badges
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SpecBadge("Contrast ${String.format("%.2f", preset.colorGrading.contrast)}")
                if (preset.grain.enabled) {
                    SpecBadge("Grain ${if (preset.grain.intensity > 0.2f) "Coarse" else "Fine"}")
                }
                if (preset.lens.vignetteIntensity > 0.05f) {
                    SpecBadge("Vignette ${(preset.lens.vignetteIntensity * 100).toInt()}%")
                }
                if (preset.halation?.enabled == true) {
                    SpecBadge("Halation")
                }
                if (preset.lightLeak?.enabled == true) {
                    SpecBadge("Light Leaks")
                }
            }
        }
    }
}

@Composable
private fun SpecBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceElevated)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary,
            maxLines = 1,
            softWrap = false
        )
    }
}
