package com.craftcam.app.ui.collection

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch

@Composable
fun CollectionScreen(
    currentPreset: CameraPreset,
    selectedLens: LensProfile = LensProfile.NORMAL,
    onSelectPreset: (CameraPreset) -> Unit,
    onSelectLens: (LensType) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<CameraCategory?>(null) }
    var expandedSamplesPresetId by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var activeLensType by remember(currentPreset.id) {
        val profile = CameraProfileCatalog.getProfileForPreset(currentPreset)
        mutableStateOf(selectedLens.type.takeIf { profile.supportedLenses.contains(it) } ?: profile.defaultLens)
    }

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

    // Scroll to the active camera card on initial entry
    LaunchedEffect(currentPreset.id) {
        val activeIdx = displayedPresets.indexOfFirst { it.id == currentPreset.id }
        if (activeIdx > 0) {
            listState.animateScrollToItem(activeIdx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Cameleo Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

            // Pinned Category Filter Tabs (Always visible at top)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    val allCount = PresetCatalog.allPresets.count { if (IS_VIDEO_FEATURE_PAUSED) it.category != CameraCategory.CAMCORDER else true }
                    CategoryFilterPill(
                        title = "ALL ($allCount)",
                        isSelected = selectedCategory == null,
                        onClick = {
                            selectedCategory = null
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    )
                }
                items(visibleCategories) { category ->
                    val catCount = PresetCatalog.allPresets.count { it.category == category }
                    CategoryFilterPill(
                        title = "${category.displayName.uppercase()} ($catCount)",
                        isSelected = selectedCategory == category,
                        onClick = {
                            selectedCategory = category
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                // All Camera Cards (Each camera appears exactly once!)
                items(displayedPresets, key = { it.id }) { preset ->
                    val isSelected = preset.id == currentPreset.id
                    val isSamplesShowing = expandedSamplesPresetId == preset.id

                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        CameraCard(
                            preset = preset,
                            isSelected = isSelected,
                            isSamplesShowing = isSamplesShowing,
                            activeLensType = activeLensType,
                            onSelect = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSelectPreset(preset)
                            },
                            onToggleSamples = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                expandedSamplesPresetId = if (isSamplesShowing) null else preset.id
                            },
                            onSelectLens = { lensType ->
                                activeLensType = lensType
                                onSelectLens(lensType)
                            },
                            onConfirm = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onNavigateBack()
                            }
                        )
                    }
                }
            }
        }

        // ==================== 4. FLOATING BOTTOM "USE CAMERA" BAR ====================
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            CameraBlack.copy(alpha = 0.88f),
                            CameraBlack
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onNavigateBack()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VintageAmber,
                    contentColor = CameraBlack
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = CameraBlack
                )
                Spacer(modifier = Modifier.width(8.dp))
                val bottomLensProfile = remember(activeLensType) { LensProfile.forType(activeLensType) }
                Text(
                    text = "START SHOOTING • ${currentPreset.name.uppercase()} (${bottomLensProfile.name.uppercase()})",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
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
    isSamplesShowing: Boolean,
    activeLensType: LensType,
    onSelect: () -> Unit,
    onToggleSamples: () -> Unit,
    onSelectLens: (LensType) -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activeLensProfile = remember(activeLensType) { LensProfile.forType(activeLensType) }
    val iconResId = remember(preset.iconResName) {
        context.resources.getIdentifier(preset.iconResName, "drawable", context.packageName)
    }

    // Resolve sample reference images (with authentic fallbacks)
    val sampleResIds = remember(preset.sampleResName, preset.category, preset.id) {
        val list = mutableListOf<Int>()
        val mainId = context.resources.getIdentifier(preset.sampleResName, "drawable", context.packageName)
        if (mainId != 0) list.add(mainId)
        for (idx in 2..4) {
            val extraId = context.resources.getIdentifier("${preset.sampleResName}_$idx", "drawable", context.packageName)
            if (extraId != 0) list.add(extraId)
        }
        if (list.isEmpty()) {
            val fallbackName = when (preset.id) {
                "analog_135ne" -> "sample_analog_faded_film"
                "inst_paf" -> "sample_inst_classic"
                "analog_s67" -> "sample_inst_soft_polaroid"
                "analog_dblack" -> "sample_disp_night"
                "analog_nt16" -> "sample_ccd_cool_digital"
                "vsco_a6" -> "sample_ccd_classic"
                "vsco_c1" -> "sample_analog_vintage_color"
                "vsco_f2" -> "sample_analog_faded_film"
                "vsco_g3" -> "sample_analog_soft_film"
                "vsco_m5" -> "sample_disp_warm"
                "vsco_hb1" -> "sample_analog_fxn_r_2"
                "vsco_b1" -> "sample_disp_night"
                "vsco_x1" -> "sample_analog_35mm_classic"
                "vsco_handycam" -> "sample_cam_retro_vhs"
                else -> when (preset.category) {
                    CameraCategory.ANALOG_FILM -> "sample_analog_35mm_classic"
                    CameraCategory.CCD_DIGITAL -> "sample_ccd_r"
                    CameraCategory.DISPOSABLE -> "sample_disp_warm"
                    CameraCategory.INSTANT -> "sample_inst_vintage"
                    CameraCategory.CAMCORDER -> "sample_cam_retro_vhs"
                    CameraCategory.VSCO_FILM -> "sample_analog_fxn_r"
                    else -> "sample_analog_35mm_classic"
                }
            }
            val fallbackId = context.resources.getIdentifier(fallbackName, "drawable", context.packageName)
            if (fallbackId != 0) list.add(fallbackId)
        }
        list
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
            .then(
                if (!isSelected) {
                    Modifier.clickable(onClick = onSelect)
                } else {
                    Modifier
                }
            )
            .padding(14.dp)
    ) {
        Column {
            // Top Row: Icon + Name + Badges + Selection indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
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
                            color = VintageAmberGlow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Sample Button & Active Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Dedicated Sample Button
                    OutlinedButton(
                        onClick = onToggleSamples,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSamplesShowing) VintageAmber.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (isSamplesShowing) VintageAmber else TextPrimary
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSamplesShowing) VintageAmber else DividerSubtle
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (isSamplesShowing) "✕ HIDE" else "📷 SAMPLE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isSamplesShowing) VintageAmber else TextPrimary
                        )
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
            }

            // Description narrative
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = if (isSelected) 5 else 2,
                overflow = TextOverflow.Ellipsis
            )

            // ==================== SAMPLE PHOTOS DISPLAY ====================
            // Shows when the user clicks the "SAMPLE" button!
            if (isSamplesShowing && sampleResIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAMPLE PHOTOGRAPHS",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = VintageAmber,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${sampleResIds.size} ${if (sampleResIds.size == 1) "PHOTO" else "PHOTOS"}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (sampleResIds.size == 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, DividerSubtle, RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            painter = painterResource(id = sampleResIds.first()),
                            contentDescription = "${preset.name} Sample",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CameraBlack.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .align(Alignment.BottomStart)
                        ) {
                            Text(
                                text = "SAMPLE REFERENCE",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = VintageAmberGlow
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(sampleResIds) { index, resId ->
                            Box(
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, DividerSubtle, RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    painter = painterResource(id = resId),
                                    contentDescription = "${preset.name} Sample ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CameraBlack.copy(alpha = 0.75f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .align(Alignment.BottomStart)
                                ) {
                                    Text(
                                        text = "${index + 1} / ${sampleResIds.size}",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = VintageAmberGlow
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==================== DIFFERENT LENSES SYSTEM ====================
            // Prominently displays the camera's supported lenses
            val profile = remember(preset.id) { CameraProfileCatalog.getProfileForPreset(preset) }
            val supportedLenses = profile.supportedLenses
            if (supportedLenses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DIFFERENT LENSES (${supportedLenses.size})",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = VintageAmber,
                        letterSpacing = 1.sp
                    )
                    if (isSelected) {
                        Text(
                            text = "MOUNTED: ${activeLensProfile.name.uppercase()}",
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(supportedLenses) { lensType ->
                        val isMounted = isSelected && activeLensType == lensType
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
                                        onSelect()
                                    }
                                    onSelectLens(lensType)
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = lensType.iconRes),
                                    contentDescription = label,
                                    modifier = Modifier.size(22.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMounted) CameraBlack else TextPrimary
                                )
                                if (isMounted) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(CameraBlack)
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "ON",
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = VintageAmber
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Specs badges
            Spacer(modifier = Modifier.height(10.dp))
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
