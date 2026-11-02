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
    onSelectPreset: (CameraPreset) -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<CameraCategory?>(null) }
    var showSamplesOnly by remember { mutableStateOf(false) }
    val view = LocalView.current
    val context = LocalContext.current

    val displayedPresets = remember(selectedCategory) {
        if (selectedCategory == null) {
            PresetCatalog.allPresets
        } else {
            PresetCatalog.allPresets.filter { it.category == selectedCategory }
        }
    }

    // 3D Model Shelves — using Dazz camera icons mapped to Cameleo presets
    val videoModels = remember {
        listOf(
            ModelShelfItem("Bessa",     "cam_bessa",     "cam_retro_vhs"),
            ModelShelfItem("QuickSnap", "cam_quicksnap", "cam_analog_video"),
            ModelShelfItem("Lomo",      "cam_lomo",      "cam_90s_camcorder"),
            ModelShelfItem("Classic U", "cam_classic_u", "ccd_y2k_digital"),
            ModelShelfItem("D FunS",    "cam_d_funs",    "ccd_flash")
        )
    }

    val photoModels = remember {
        listOf(
            ModelShelfItem("D Classic", "cam_d_classic", "analog_35mm_classic"),
            ModelShelfItem("D Slide",   "cam_d_slide",   "inst_classic"),
            ModelShelfItem("D Exp",     "cam_d_exp",     "analog_vintage_color"),
            ModelShelfItem("Polaroid",  "cam_polaroid",  "inst_vintage"),
            ModelShelfItem("TLR",       "cam_tlr",       "inst_soft_polaroid")
        )
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
                            .size(34.dp)
                            .clip(CircleShape)
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                // SAMPLE toggle button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (showSamplesOnly) VintageAmber else SurfaceElevated)
                        .border(1.dp, DividerSubtle, RoundedCornerShape(16.dp))
                        .clickable {
                            showSamplesOnly = !showSamplesOnly
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "📷 SAMPLE",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (showSamplesOnly) CameraBlack else TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

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
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ==================== 1. ▷ VIDEO SHELF ====================
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

            // ==================== 2. 📷 PHOTO SHELF ====================
            item {
                ShelfSectionHeader(title = "📷 Photo")
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(photoModels) { item ->
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
                ShelfSectionHeader(title = "🎞 All Camera Emulsions (${PresetCatalog.allPresets.size})")
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
                    items(CameraCategory.values()) { category ->
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
                        showSampleAlways = showSamplesOnly,
                        onSelect = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSelectPreset(preset)
                            onNavigateBack()
                        }
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
                    .size(width = 68.dp, height = 50.dp)
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
    showSampleAlways: Boolean,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
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
                                .size(42.dp)
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

            Spacer(modifier = Modifier.height(10.dp))

            // Optical & Chemical Emulsion Specification Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
            color = TextSecondary
        )
    }
}
