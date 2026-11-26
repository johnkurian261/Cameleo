package com.craftcam.app.ui.collection

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/**
 * CAMELEO Camera Gallery / Camera Selection Screen.
 *
 * Implements a Dazz-inspired physical camera browsing experience:
 * - Pure deep black canvas showcasing transparent vintage camera artwork.
 * - Horizontal browsing shelves organized by authentic camera categories.
 * - Minimal, high-readability name pills (dark pill unselected, solid white pill when active).
 * - Direct sample photo access via [📷 SAMPLE] button opening an edge-to-edge photo stream.
 * - Sleek top Pro banner, minimal category switcher [☰], and bottom-right quick tool controls.
 * - Color Profile & Different Lenses bottom sheet for mounting accessories.
 * - Down arrow [∨] and direct camera tap to start shooting immediately.
 */
@Composable
fun CollectionScreen(
    currentPreset: CameraPreset,
    filmControlsAvailable: Boolean,
    selectedLens: LensProfile = LensProfile.NORMAL,
    frameEnabled: Boolean = true,
    strictExposure: Boolean = false,
    onStrictExposure: (Boolean) -> Unit = {},
    grainEnabled: Boolean = true,
    colorProfileIndex: Int = 1,
    onSelectPreset: (CameraPreset) -> Unit,
    onSelectLens: (LensType) -> Unit = {},
    onToggleFrame: (Boolean) -> Unit = {},
    onToggleGrain: (Boolean) -> Unit = {},
    onSelectColorProfile: (Int) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var selectedPresetState by remember(currentPreset.id) { mutableStateOf(currentPreset) }
    var selectedCategory by remember { mutableStateOf<CameraCategory?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var managingCameras by remember { mutableStateOf(false) }
    val preferences = remember(context) { context.getSharedPreferences("camera_shelf", android.content.Context.MODE_PRIVATE) }
    var hidden by remember { mutableStateOf(preferences.getStringSet("hidden", emptySet()).orEmpty().toSet()) }
    var showSampleViewer by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var bottomSheetTab by remember { mutableStateOf(BottomSheetType.COLOR_PROFILE) }
    var activeFrameEnabled by remember(frameEnabled) { mutableStateOf(frameEnabled) }
    var activeGrainEnabled by remember(grainEnabled) { mutableStateOf(grainEnabled) }
    var selectedColorProfileIndex by remember(selectedPresetState.id, colorProfileIndex) {
        val savedId = com.craftcam.app.config.AppSettingsStore.get(context).getColorProfileForCamera(selectedPresetState.id)
        val prof = com.craftcam.app.color.profile.DazzColorProfileCatalog.getById(savedId)
        mutableStateOf(prof.index)
    }

    var activeLensType by remember(selectedPresetState.id) {
        val profile = CameraProfileCatalog.getProfileForPreset(selectedPresetState)
        mutableStateOf(selectedLens.type.takeIf { profile.supportedLenses.contains(it) } ?: profile.defaultLens)
    }

    val visibleCategories = remember {
        if (IS_VIDEO_FEATURE_PAUSED) {
            CameraCategory.values().filter { it != CameraCategory.CAMCORDER }
        } else {
            CameraCategory.values().toList()
        }
    }

    val allAvailablePresets = remember {
        if (IS_VIDEO_FEATURE_PAUSED) {
            PresetCatalog.allPresets.filter { it.category != CameraCategory.CAMCORDER }
        } else {
            PresetCatalog.allPresets
        }
    }

    val shelf = remember(allAvailablePresets, hidden, currentPreset.id) {
        allAvailablePresets.filter { it.id == currentPreset.id || it.id !in hidden }
    }

    val activeCategories = remember(visibleCategories, shelf) {
        visibleCategories.filter { cat -> shelf.any { it.category == cat } }
    }

    // Resolves authentic sample images for the selected preset
    val currentSampleResIds = remember(selectedPresetState.id) {
        resolveSampleResIds(context, selectedPresetState)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ==================== ACTION CONTROLS BAR ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Label / Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showCategoryMenu = !showCategoryMenu
                    }
                ) {
                    Text(
                        text = (selectedCategory?.displayName ?: "ALL CAMERAS").uppercase(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showCategoryMenu) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Categories",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Right controls: [📷 SAMPLE] & [☰]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // SAMPLE Button
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222224))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showSampleViewer = true
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Sample",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "SAMPLE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Hamburger Menu Button [☰]
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showCategoryMenu = false
                            managingCameras = true
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222224))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Manage cameras",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Quick Category Filter Tabs (when opened or pinned)
            AnimatedVisibility(
                visible = showCategoryMenu,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        CategoryTabPill(
                            title = "ALL (${shelf.size})",
                            isSelected = selectedCategory == null,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedCategory = null
                                showCategoryMenu = false
                            }
                        )
                    }
                    items(activeCategories, key = { it.name }) { cat ->
                        val count = shelf.count { it.category == cat }
                        CategoryTabPill(
                            title = "${cat.displayName.uppercase()} ($count)",
                            isSelected = selectedCategory == cat,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedCategory = cat
                                showCategoryMenu = false
                            }
                        )
                    }
                }
            }

            // ==================== 3. CAMERA BROWSING SHELVES ====================
            // Smooth vertical column of categories, each containing horizontal camera shelves
            val categoriesToDisplay = remember(selectedCategory) {
                if (selectedCategory != null) {
                    listOf(selectedCategory!!)
                } else {
                    visibleCategories
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(categoriesToDisplay, key = { it.name }) { category ->
                    val presetsInCategory = remember(category, shelf) {
                        shelf.filter { it.category == category }
                    }

                    if (presetsInCategory.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Section Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category.displayName.uppercase(),
                                    color = Color(0xFF8E8E93),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(0.6.dp)
                                        .background(Color(0xFF222224))
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Horizontal Camera Shelf
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(presetsInCategory, key = { it.id }) { preset ->
                                    val isSelected = preset.id == selectedPresetState.id

                                    CameraShelfItem(
                                        preset = preset,
                                        isSelected = isSelected,
                                        onSelect = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            selectedPresetState = preset
                                            onSelectPreset(preset)
                                            val prof = CameraProfileCatalog.getProfileForPreset(preset)
                                            activeLensType = prof.defaultLens
                                            val savedProfileId = com.craftcam.app.config.AppSettingsStore.get(context).getColorProfileForCamera(preset.id)
                                            val savedProfile = com.craftcam.app.color.profile.DazzColorProfileCatalog.getById(savedProfileId)
                                            selectedColorProfileIndex = savedProfile.index
                                        },
                                        onConfirm = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            onSelectPreset(preset)
                                            onNavigateBack()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== 4. FLOATING BOTTOM CONTROLS ====================
        // Clean Dazz-style bottom cluster: Color Profile [3 rings], Lenses [frame], Down [∨]
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222224))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        bottomSheetTab = BottomSheetType.COLOR_PROFILE
                        showBottomSheet = true
                    },
                contentAlignment = Alignment.Center
            ) {
                ColorProfileRingsIcon(modifier = Modifier.size(24.dp).semantics {
                    contentDescription = "Colour profiles and grain"
                })
            }

            // Frame / Lenses Button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222224))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        bottomSheetTab = BottomSheetType.LENSES
                        showBottomSheet = true
                    },
                contentAlignment = Alignment.Center
            ) {
                FrameSquareIcon(modifier = Modifier.size(22.dp))
            }

            // Circular Down Arrow [∨] Confirm & Shoot Button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222224))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onNavigateBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Confirm & Start Shooting",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        if (managingCameras) {
            AlertDialog(
                onDismissRequest = { managingCameras = false },
                title = { Text("Manage cameras") },
                text = {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(allAvailablePresets, key = { it.id }) { preset ->
                            val active = preset.id == currentPreset.id
                            val shown = active || preset.id !in hidden
                            Row(Modifier.fillMaxWidth().clickable(enabled = !active) {
                                hidden = if (shown) hidden + preset.id else hidden - preset.id
                                preferences.edit().putStringSet("hidden", hidden).apply()
                            }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(preset.name)
                                    if (active) Text("Current camera", style = MaterialTheme.typography.labelSmall)
                                }
                                Checkbox(checked = shown, onCheckedChange = null, enabled = !active)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { managingCameras = false }) { Text("Done") } },
                dismissButton = { TextButton(onClick = {
                    hidden = emptySet()
                    preferences.edit().remove("hidden").apply()
                }) { Text("Show all") } }
            )
        }

        // ==================== 5. FULL-SCREEN SAMPLE PHOTOS VIEWER ====================
        if (showSampleViewer) {
            SamplePhotosViewer(
                preset = selectedPresetState,
                sampleResIds = currentSampleResIds,
                onClose = { showSampleViewer = false }
            )
        }

        // ==================== 6. COLOR PROFILE & LENSES BOTTOM SHEET ====================
        if (showBottomSheet) {
            ColorProfileAndLensSheet(
                preset = selectedPresetState,
                activeLensType = activeLensType,
                initialTab = bottomSheetTab,
                filmControlsAvailable = filmControlsAvailable,
                selectedColorProfileIndex = selectedColorProfileIndex,
                frameEnabled = activeFrameEnabled,
                strictExposure = strictExposure,
                onStrictExposure = onStrictExposure,
                grainEnabled = activeGrainEnabled,
                onSelectColorProfile = { index ->
                    selectedColorProfileIndex = index
                    onSelectColorProfile(index)
                },
                onToggleFrame = { enabled ->
                    activeFrameEnabled = enabled
                    onToggleFrame(enabled)
                },
                onToggleGrain = { enabled ->
                    activeGrainEnabled = enabled
                    onToggleGrain(enabled)
                },
                onSelectLens = { lensType ->
                    activeLensType = lensType
                    onSelectLens(lensType)
                },
                onDismiss = { showBottomSheet = false }
            )
        }
    }
}

/**
 * Individual Camera Shelf Item:
 * - Transparent vintage camera artwork floating directly on dark canvas.
 * - Pill beneath: unselected is dark pill, selected is bright white pill with bold black text.
 */
@Composable
private fun CameraShelfItem(
    preset: CameraPreset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val iconResId = remember(preset.iconResName) {
        context.resources.getIdentifier(preset.iconResName, "drawable", context.packageName)
    }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "cam_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(86.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isSelected) {
                    onConfirm()
                } else {
                    onSelect()
                }
            }
    ) {
        // Physical Camera Artwork
        Box(
            modifier = Modifier
                .height(68.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (iconResId != 0) {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = preset.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scale)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = preset.name,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Name Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Color.White else Color(0xFF1A1B1F))
                .border(
                    width = 0.8.dp,
                    color = if (isSelected) Color.White else Color(0xFF2C2D32),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = preset.name,
                color = if (isSelected) Color.Black else Color(0xFFE5E5EA),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Filter Pill for Categories.
 */
@Composable
private fun CategoryTabPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.White else Color(0xFF1E1F23))
            .border(
                width = 0.8.dp,
                color = if (isSelected) Color.White else Color(0xFF2E2F35),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.Black else Color(0xFFB0B0B5),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Full-screen Sample Photography Viewer matching Images 3 & 4.
 */
@Composable
private fun SamplePhotosViewer(
    preset: CameraPreset,
    sampleResIds: List<Int>,
    onClose: () -> Unit
) {
    val view = LocalView.current
    val isMonochrome = preset.id in listOf("analog_dblack", "vsco_b1", "vsco_x1") || preset.colorGrading.saturation <= 0.05f
    val sampleColorFilter = remember(isMonochrome) {
        if (isMonochrome) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 0.dp, bottom = 90.dp)
        ) {
            // Header showing the specific filter name
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onClose()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dazz_back),
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${preset.name.uppercase()} SAMPLES",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            // Vertical continuous contact sheet of large sample images
            if (sampleResIds.isNotEmpty()) {
                itemsIndexed(sampleResIds) { index, resId ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        Image(
                            painter = painterResource(id = resId),
                            contentDescription = "${preset.name} Sample ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            colorFilter = sampleColorFilter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp)
                        )
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO SAMPLE IMAGES AVAILABLE",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Footer info matching Images 3 & 4
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Samples by",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF00C7BE), Color(0xFF007AFF), Color(0xFFFF3B30))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "D",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = "dazzz",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "•",
                            color = Color(0xFF8E8E93),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Instagram camera outline icon
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Instagram",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF222224))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "+ SUBMIT MY WORKS",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // Floating Close Button [✕] in bottom right matching Image 3 & 4
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClose()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFF222224))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Sample Viewer",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Color Profile and Different Lenses Bottom Sheet matching Images 2 & 5.
 */
enum class BottomSheetType {
    COLOR_PROFILE,
    LENSES
}

@Composable
private fun ColorProfileAndLensSheet(
    preset: CameraPreset,
    activeLensType: LensType,
    initialTab: BottomSheetType,
    filmControlsAvailable: Boolean,
    selectedColorProfileIndex: Int,
    frameEnabled: Boolean,
    strictExposure: Boolean,
    onStrictExposure: (Boolean) -> Unit,
    grainEnabled: Boolean,
    onSelectColorProfile: (Int) -> Unit,
    onToggleFrame: (Boolean) -> Unit,
    onToggleGrain: (Boolean) -> Unit,
    onSelectLens: (LensType) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val profile = remember(preset.id) { CameraProfileCatalog.getProfileForPreset(preset) }
    val supportedLenses = profile.supportedLenses

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF16171A))
                .border(
                    width = 0.8.dp,
                    color = Color(0xFF2A2B30),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF44444A))
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (initialTab == BottomSheetType.COLOR_PROFILE) {
                // ==================== COLOR PROFILE TAB (Dazz Cam Color Scheme) ====================
                val context = LocalView.current.context
                val defaultProfile = remember(preset.id) {
                    com.craftcam.app.color.profile.CameraProfileCompatibility.getDefaultProfile(preset.id)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (filmControlsAvailable) "Color profile" else "Color profile · Film cameras",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedColorProfileIndex != defaultProfile.index) {
                        Text(
                            text = "Reset",
                            color = Color(0xFF8E8E93),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onSelectColorProfile(defaultProfile.index)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Film Profile Swatches Row (Camera-Specific Compatibility)
                val supportedProfiles = remember(preset.id) {
                    com.craftcam.app.color.profile.CameraProfileCompatibility.getSupportedProfiles(preset.id).ifEmpty {
                        com.craftcam.app.color.profile.DazzColorProfileCatalog.PROFILES
                    }
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(supportedProfiles, key = { it.id }) { profile ->
                        val thumbRes = remember(profile.thumbnailDrawableName) {
                            context.resources.getIdentifier(profile.thumbnailDrawableName, "drawable", context.packageName)
                        }
                        ColorProfileCard(
                            label = profile.displayName,
                            topColor = profile.topColor,
                            bodyColor = profile.bodyColor,
                            thumbnailResId = if (thumbRes != 0) thumbRes else null,
                            isSelected = selectedColorProfileIndex == profile.index,
                            hasRainbow = profile.hasRainbow,
                            onClick = { onSelectColorProfile(profile.index) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val selectedProfile = com.craftcam.app.color.profile.DazzColorProfileCatalog.getByIndex(selectedColorProfileIndex)

                Text(
                    text = selectedProfile.description,
                    color = Color(0xFF8E8E93),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(Color(0xFF26272C))
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (preset.id == "ccd_flash") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Fixed film exposure", color = Color.White)
                            Text("RAW stills in ambient light", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Switch(checked = strictExposure, onCheckedChange = onStrictExposure)
                    }
                }

                // Section 2: Frame & Grain Options (Images 2 & 5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Frame Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Frame",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Off Option (circle with diagonal slash ⊘)
                            OptionToggleBox(
                                isSelected = !frameEnabled,
                                onClick = { onToggleFrame(false) }
                            ) {
                                DisabledSlashIcon(modifier = Modifier.size(24.dp))
                            }

                            // On Option (Frame border preview)
                            OptionToggleBox(
                                isSelected = frameEnabled,
                                onClick = { onToggleFrame(true) }
                            ) {
                                FrameThumbnail()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Grain Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Grain",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Off Option (circle with diagonal slash ⊘)
                            OptionToggleBox(
                                isSelected = !grainEnabled,
                                label = "Grain off",
                                onClick = { onToggleGrain(false) }
                            ) {
                                DisabledSlashIcon(modifier = Modifier.size(24.dp))
                            }

                            // On Option (Grain texture preview)
                            OptionToggleBox(
                                isSelected = grainEnabled,
                                label = "Grain on",
                                onClick = { onToggleGrain(true) }
                            ) {
                                GrainThumbnail()
                            }
                        }
                    }
                }
            } else {
                // ==================== LENSES & RATIOS TAB ====================
                Text(
                    text = "Different Lenses (${supportedLenses.size})",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(supportedLenses, key = { it.name }) { lensType ->
                        val isMounted = activeLensType == lensType

                        Box(
                            modifier = Modifier
                                .width(78.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isMounted) Color(0xFF25272D) else Color(0xFF1E1F23))
                                .border(
                                    width = if (isMounted) 1.5.dp else 0.8.dp,
                                    color = if (isMounted) Color.White else Color(0xFF2E2F35),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onSelectLens(lensType)
                                }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = lensType.iconRes),
                                    contentDescription = lensType.displayName,
                                    modifier = Modifier.size(26.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lensType.displayName,
                                    color = if (isMounted) Color.White else Color(0xFF8E8E93),
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            if (isMounted) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Mounted",
                                        tint = Color.Black,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Clean Canvas circle with diagonal slash icon for ⊘ Disabled toggle.
 */
@Composable
private fun DisabledSlashIcon(modifier: Modifier = Modifier, color: Color = Color(0xFF8E8E93)) {
    Canvas(modifier = modifier) {
        val r = size.minDimension * 0.38f
        val stroke = Stroke(width = 1.8.dp.toPx())
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = color, radius = r, center = center, style = stroke)
        val d = r * 0.7071f
        drawLine(
            color = color,
            start = Offset(center.x - d, center.y - d),
            end = Offset(center.x + d, center.y + d),
            strokeWidth = 1.8.dp.toPx()
        )
    }
}

/**
 * Thumbnail preview for photographic frame border.
 */
@Composable
private fun FrameThumbnail(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .padding(top = 3.dp, start = 3.dp, end = 3.dp, bottom = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF222328))
        )
    }
}

/**
 * Thumbnail preview for authentic silver-halide film grain texture.
 */
@Composable
private fun GrainThumbnail(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF48484E))
    ) {
        val rnd = java.util.Random(1337L)
        for (i in 0 until 140) {
            val gx = rnd.nextFloat() * size.width
            val gy = rnd.nextFloat() * size.height
            val isWhite = rnd.nextBoolean()
            val alpha = 0.35f + rnd.nextFloat() * 0.45f
            drawCircle(
                color = if (isWhite) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha),
                radius = 0.9f + rnd.nextFloat() * 0.6f,
                center = Offset(gx, gy)
            )
        }
    }
}

/**
 * Reusable toggle button for Frame and Grain options with checkmark badge.
 */
@Composable
private fun OptionToggleBox(
    isSelected: Boolean,
    onClick: () -> Unit,
    label: String? = null,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .size(62.dp)
            .semantics { if (label != null) contentDescription = label }
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF222328))
            .border(
                width = if (isSelected) 1.2.dp else 0.8.dp,
                color = if (isSelected) Color.White else Color(0xFF2E3036),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        content()

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.Black,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

/**
 * Single film profile swatch card matching Images 2 & 5.
 */
@Composable
private fun ColorProfileCard(
    label: String,
    topColor: Color,
    bodyColor: Color,
    isSelected: Boolean,
    hasRainbow: Boolean = false,
    thumbnailResId: Int? = null,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .width(74.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bodyColor)
            .border(
                width = if (isSelected) 1.5.dp else 0.8.dp,
                color = if (isSelected) Color.White else Color(0xFF2E3036),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
    ) {
        if (thumbnailResId != null) {
            Image(
                painter = painterResource(id = thumbnailResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.82f,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.60f)
                            )
                        )
                    )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(if (thumbnailResId != null) topColor.copy(alpha = 0.85f) else topColor)
            ) {
                if (hasRainbow) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFFF3B30),
                                        Color(0xFFFF9500),
                                        Color(0xFFFFCC00),
                                        Color(0xFF34C759),
                                        Color(0xFF00C7BE),
                                        Color(0xFF007AFF),
                                        Color(0xFF5856D6),
                                        Color(0xFFAF52DE)
                                    )
                                )
                            )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.Black,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

/**
 * Canvas drawing for the 3 overlapping colored rings (RGB Venn diagram) matching Image 1.
 */
@Composable
private fun ColorProfileRingsIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension * 0.28f
        val offset = size.minDimension * 0.13f
        val stroke = Stroke(width = 1.8.dp.toPx())

        // Top: Red ring
        drawCircle(
            color = Color(0xFFFF3B30),
            radius = r,
            center = Offset(size.width / 2f, size.height / 2f - offset),
            style = stroke
        )
        // Bottom left: Green ring
        drawCircle(
            color = Color(0xFF34C759),
            radius = r,
            center = Offset(size.width / 2f - offset * 0.9f, size.height / 2f + offset * 0.6f),
            style = stroke
        )
        // Bottom right: Blue ring
        drawCircle(
            color = Color(0xFF007AFF),
            radius = r,
            center = Offset(size.width / 2f + offset * 0.9f, size.height / 2f + offset * 0.6f),
            style = stroke
        )
    }
}

/**
 * Canvas drawing for concentric frame square icon matching Image 1.
 */
@Composable
private fun FrameSquareIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outer = size.minDimension * 0.70f
        val inner = size.minDimension * 0.42f
        val stroke = Stroke(width = 1.6.dp.toPx())
        val outerLeft = (size.width - outer) / 2f
        val outerTop = (size.height - outer) / 2f
        val innerLeft = (size.width - inner) / 2f
        val innerTop = (size.height - inner) / 2f

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(outerLeft, outerTop),
            size = Size(outer, outer),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = stroke
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(innerLeft, innerTop),
            size = Size(inner, inner),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = stroke
        )
    }
}

/**
 * Helper to resolve authentic sample images for each preset with filter-specific curated mappings.
 */
private fun resolveSampleResIds(context: android.content.Context, preset: CameraPreset): List<Int> {
    val list = mutableListOf<Int>()

    // Filter-specific curated authentic sample image sets
    val specificDrawables: List<String> = when (preset.id) {
        "zero" -> listOf("sample_zero", "sample_analog_35mm_classic", "sample_ccd_classic")
        "analog_fxn_r", "fxnr" -> listOf("sample_analog_fxn_r", "sample_analog_fxn_r_2", "sample_analog_fxn_r_3", "sample_analog_fxn_r_4")
        "analog_35mm_classic", "d_classic" -> listOf("sample_analog_35mm_classic", "sample_analog_warm_film", "sample_analog_faded_film")
        "analog_cinema_film", "d3d" -> listOf("sample_analog_cinema_film", "sample_analog_fxn_r_3", "sample_analog_vintage_color")
        "analog_135ne", "135ne" -> listOf("sample_analog_warm_film", "sample_analog_35mm_classic", "sample_disp_warm")
        "analog_soft_film", "ct2f" -> listOf("sample_analog_soft_film", "sample_analog_faded_film", "sample_analog_35mm_classic")
        "disp_warm", "d_funs" -> listOf("sample_disp_warm", "sample_disp_classic", "sample_disp_flash")
        "analog_warm_film", "grd" -> listOf("sample_analog_warm_film", "sample_ccd_retro_compact", "sample_ccd_r")
        "ccd_r" -> listOf("sample_ccd_r", "sample_ccd_classic", "sample_ccd_flash")
        "ccd_y2k_digital", "classic_u" -> listOf("sample_ccd_y2k_digital", "sample_ccd_cool_digital", "sample_ccd_r")
        "ccd_flash" -> listOf("sample_ccd_flash", "sample_disp_flash", "sample_ccd_r")
        "ccd_retro_compact", "dqs" -> listOf("sample_ccd_retro_compact", "sample_ccd_classic", "sample_ccd_y2k_digital")
        "disp_flash" -> listOf("sample_disp_flash", "sample_disp_night", "sample_disp_classic")
        "disp_classic" -> listOf("sample_disp_classic", "sample_disp_warm", "sample_disp_flash")
        "analog_fisheye_f", "fisheye" -> listOf("sample_analog_fisheye_f", "sample_disp_warm", "sample_disp_night")
        "inst_vintage", "inst_c" -> listOf("sample_inst_vintage", "sample_inst_classic", "sample_inst_soft_polaroid")
        "inst_paf" -> listOf("sample_inst_soft_polaroid", "sample_inst_classic", "sample_inst_vintage")
        "analog_vintage_color", "dexp" -> listOf("sample_analog_vintage_color", "sample_analog_faded_film", "sample_analog_soft_film")
        "analog_s67", "s67" -> listOf("sample_analog_35mm_classic", "sample_analog_warm_film", "sample_analog_vintage_color")
        "analog_dblack", "dblack" -> listOf("sample_analog_35mm_classic", "sample_analog_faded_film", "sample_analog_warm_film")
        "analog_nt16", "nt16" -> listOf("sample_disp_night", "sample_analog_fxn_r_4", "sample_analog_cinema_film")
        "vsco_a6", "a6" -> listOf("sample_analog_warm_film", "sample_analog_soft_film", "sample_analog_35mm_classic")
        "vsco_c1", "c1" -> listOf("sample_analog_vintage_color", "sample_ccd_flash", "sample_disp_warm")
        "vsco_f2", "f2" -> listOf("sample_analog_faded_film", "sample_analog_soft_film", "sample_analog_fxn_r_2")
        "vsco_g3", "g3" -> listOf("sample_analog_soft_film", "sample_analog_warm_film", "sample_analog_35mm_classic")
        "vsco_m5", "m5" -> listOf("sample_analog_vintage_color", "sample_analog_cinema_film", "sample_analog_faded_film")
        "vsco_hb1", "hb1" -> listOf("sample_analog_fxn_r_2", "sample_analog_fxn_r_3", "sample_analog_cinema_film")
        "vsco_b1", "b1" -> listOf("sample_analog_35mm_classic", "sample_analog_fxn_r_2", "sample_analog_vintage_color")
        "vsco_x1", "x1" -> listOf("sample_analog_faded_film", "sample_analog_35mm_classic", "sample_analog_fxn_r_4")
        "vsco_handycam", "handycam" -> listOf("sample_cam_90s_camcorder", "sample_cam_analog_video", "sample_cam_retro_vhs")
        "cam_retro_vhs", "vhs" -> listOf("sample_cam_retro_vhs", "sample_cam_analog_video", "sample_cam_90s_camcorder")
        "cam_analog_video" -> listOf("sample_cam_analog_video", "sample_cam_retro_vhs", "sample_cam_90s_camcorder")
        "cam_90s_camcorder" -> listOf("sample_cam_90s_camcorder", "sample_cam_analog_video", "sample_cam_retro_vhs")
        else -> listOf("sample_${preset.id}", "sample_analog_35mm_classic", "sample_analog_fxn_r_2")
    }

    for (name in specificDrawables) {
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id != 0 && !list.contains(id)) {
            list.add(id)
        }
    }
    return list
}
