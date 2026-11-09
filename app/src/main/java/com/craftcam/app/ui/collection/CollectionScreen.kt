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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    selectedLens: LensProfile = LensProfile.NORMAL,
    onSelectPreset: (CameraPreset) -> Unit,
    onSelectLens: (LensType) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var selectedPresetState by remember { mutableStateOf(currentPreset) }
    var selectedCategory by remember { mutableStateOf<CameraCategory?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showSampleViewer by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var bottomSheetTab by remember { mutableStateOf(BottomSheetType.COLOR_PROFILE) }

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
            // ==================== 1. TOP PRO BANNER ====================
            TopProBanner(context = context)

            // ==================== 2. ACTION CONTROLS BAR ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
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
                            showCategoryMenu = !showCategoryMenu
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222224))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Category Filter",
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
                            title = "ALL (${allAvailablePresets.size})",
                            isSelected = selectedCategory == null,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedCategory = null
                                showCategoryMenu = false
                            }
                        )
                    }
                    items(visibleCategories) { cat ->
                        val count = allAvailablePresets.count { it.category == cat }
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
                    val presetsInCategory = remember(category, allAvailablePresets) {
                        allAvailablePresets.filter { it.category == category }
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
            // Color Profile Button (3 RGB rings)
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
                ColorProfileRingsIcon(modifier = Modifier.size(24.dp))
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

        // Bottom Left Active Camera Pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 30.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161719).copy(alpha = 0.90f))
                .border(0.8.dp, Color(0xFF2A2B30), RoundedCornerShape(16.dp))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onNavigateBack()
                }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(VintageAmber)
                )
                val currentLensProfile = remember(activeLensType) { LensProfile.forType(activeLensType) }
                Text(
                    text = "${selectedPresetState.name.uppercase()} • ${currentLensProfile.name.uppercase()}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
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
 * Top Pro Banner matching Dazz reference: gradient card from royal blue to crimson.
 */
@Composable
private fun TopProBanner(context: android.content.Context) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF0F4C81),
                        Color(0xFF6A2C70),
                        Color(0xFFB83B5E),
                        Color(0xFFD63031)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val logoResId = remember {
                    context.resources.getIdentifier("cameleo_logo", "drawable", context.packageName)
                }
                if (logoResId != 0) {
                    Image(
                        painter = painterResource(id = logoResId),
                        contentDescription = "Cameleo Logo",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column {
                    Text(
                        text = "Cameleo Pro",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Unlock all Cameras & Accessories.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = preset.name.uppercase(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = preset.subtitle,
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // Vertical list of large sample images
            if (sampleResIds.isNotEmpty()) {
                itemsIndexed(sampleResIds) { index, resId ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(0.6.dp, Color(0xFF222224), RoundedCornerShape(6.dp))
                    ) {
                        Image(
                            painter = painterResource(id = resId),
                            contentDescription = "${preset.name} Sample ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp, max = 340.dp)
                        )
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
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

            // Footer info matching reference
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Samples by",
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(VintageAmber),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "C",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Cameleo",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF222224))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "+ CAMELEO ARCHIVE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // Floating Close Button [✕] in bottom right
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClose()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF222224))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Sample Viewer",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
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

            // Section 1: Color Profile
            Text(
                text = "Color Profile",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Film Profile Swatches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilmProfileCard(label = "03 G", headerColor = Color(0xFF8B5A2B), isSelected = false)
                FilmProfileCard(label = "C400", headerColor = Color(0xFF00C853), isSelected = true)
                FilmProfileCard(label = "07 FF", headerColor = Color(0xFF00B4D8), isSelected = false)
                FilmProfileCard(label = "19 P", headerColor = Color(0xFF7B2CBF), isSelected = false)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = preset.description.ifEmpty {
                    "Daylight-balanced motion picture film, with vibrant color rendering based on scans from a standard film scanner."
                },
                color = Color(0xFF8E8E93),
                fontSize = 11.sp,
                lineHeight = 15.sp,
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

            // Section 2: Different Lenses
            Text(
                text = "Different Lenses (${supportedLenses.size})",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(supportedLenses) { lensType ->
                    val isMounted = activeLensType == lensType

                    Box(
                        modifier = Modifier
                            .width(76.dp)
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
                                modifier = Modifier.size(24.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = lensType.displayName,
                                color = if (isMounted) Color.White else Color(0xFF8E8E93),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        if (isMounted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Mounted",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Single film profile swatch card matching Image 2 & 5.
 */
@Composable
private fun FilmProfileCard(
    label: String,
    headerColor: Color,
    isSelected: Boolean
) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF222328))
            .border(
                width = if (isSelected) 1.2.dp else 0.8.dp,
                color = if (isSelected) Color.White else Color(0xFF2E3036),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(headerColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(12.dp)
            )
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
 * Helper to resolve authentic sample images for each preset with fallback mappings.
 */
private fun resolveSampleResIds(context: android.content.Context, preset: CameraPreset): List<Int> {
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
    return list
}
