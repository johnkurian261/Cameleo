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
    var expandedSamplesPresetId by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val photoShelfState = rememberLazyListState()

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

    // Complete Photo Camera models shelf — includes EVERY photo camera from Dazz Cam
    val photoModels = remember {
        val photoPresets = PresetCatalog.allPresets.filter { it.category != CameraCategory.CAMCORDER }
        photoPresets.map { preset ->
            ModelShelfItem(
                label = preset.name,
                iconResName = preset.iconResName,
                presetId = preset.id
            )
        }
    }

    // Smoothly scroll shelf to active camera on launch or selection change
    LaunchedEffect(currentPreset.id, photoModels) {
        val activeIdx = photoModels.indexOfFirst { it.presetId == currentPreset.id }
        if (activeIdx >= 0) {
            photoShelfState.animateScrollToItem(activeIdx)
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                // ==================== 1. 📷 PHOTO CAMERAS 3D SHELF ====================
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
                                    val targetPreset = PresetCatalog.getPresetById(item.presetId)
                                    onSelectPreset(targetPreset)
                                    if (selectedCategory != null && targetPreset.category != selectedCategory) {
                                        selectedCategory = null
                                    }
                                    val cardIndex = displayedPresets.indexOfFirst { it.id == item.presetId }
                                    if (cardIndex >= 0) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem((cardIndex + 2).coerceAtLeast(0))
                                        }
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ==================== 2. CATALOG FILTER TABS ====================
                item {
                    ShelfSectionHeader(
                        title = "🎞 All Cameras (${displayedPresets.size})"
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

                // ==================== 3. ALL CAMERA CARDS WITH LENSES & SAMPLE BUTTON ====================
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
                .background(if (isSelected) VintageAmber else SurfaceCard)
                .border(
                    width = 1.dp,
                    color = if (isSelected) VintageAmberGlow else DividerSubtle,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                text = item.label,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) CameraBlack else TextSecondary
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
            val fallbackName = when (preset.category) {
                CameraCategory.ANALOG_FILM -> "sample_analog_35mm_classic"
                CameraCategory.CCD_DIGITAL -> "sample_ccd_r"
                CameraCategory.DISPOSABLE -> "sample_disp_warm"
                CameraCategory.INSTANT -> "sample_inst_vintage"
                CameraCategory.CAMCORDER -> "sample_cam_retro_vhs"
                CameraCategory.VSCO_FILM -> "sample_analog_fxn_r"
                else -> "sample_analog_35mm_classic"
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

            // If selected: Direct "USE CAMERA" confirmation button
            if (isSelected) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VintageAmber,
                        contentColor = CameraBlack
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = CameraBlack
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "USE ${preset.name.uppercase()} • ${activeLensProfile.name.uppercase()}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
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
