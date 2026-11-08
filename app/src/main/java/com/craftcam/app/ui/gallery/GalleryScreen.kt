package com.craftcam.app.ui.gallery

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.craftcam.app.R
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.processor.ImageProcessor
import com.craftcam.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GalleryItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val cameraModel: String? = null,
    val isVideo: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var allPhotos by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // Redevelop state
    var redevelopingItem by remember { mutableStateOf<GalleryItem?>(null) }
    var isRedeveloping by remember { mutableStateOf(false) }

    // Import Photo state
    var isImporting by remember { mutableStateOf(false) }

    // Load initial photos
    LaunchedEffect(Unit) {
        allPhotos = loadCameleoPhotos(context)
        isLoading = false
    }

    // Photo picker launcher for importing photos
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isImporting = true
                try {
                    val defaultPreset = PresetCatalog.allPresets.find { it.id == "analog_fxn_r" }
                        ?: PresetCatalog.allPresets.first()
                    val newUri = importAndDevelopPhoto(context, uri, defaultPreset)
                    if (newUri != null) {
                        allPhotos = loadCameleoPhotos(context)
                        Toast.makeText(context, "Developed with ${defaultPreset.name}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import photo", Toast.LENGTH_SHORT).show()
                } finally {
                    isImporting = false
                }
            }
        }
    }

    // Delete single photo
    val onDeletePhoto: (GalleryItem) -> Unit = { item ->
        deletePhoto(context, item.uri)
        val updated = allPhotos.filter { it.id != item.id }
        allPhotos = updated
        if (selectedIndex != null && updated.isEmpty()) {
            selectedIndex = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── TOP NAVIGATION BAR (Simple, No Bubbles) ──────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!isSelectionMode) {
                    // Left: Return to camera button (clean back arrow, NO bubble)
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dazz_back),
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Center: Clean Darkroom title
                    Text(
                        text = "DARKROOM",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    // Right: Import & Select Buttons (simple icons, NO bubble background/border)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_dazz_importphoto),
                                contentDescription = "Import Photo",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = { isSelectionMode = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_dazz_select),
                                contentDescription = "Select Mode",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else {
                    // Selection Mode Header
                    TextButton(
                        onClick = {
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }
                    ) {
                        Text(
                            text = "CANCEL",
                            color = VintageAmber,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${selectedIds.size} SELECTED",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(
                        onClick = {
                            selectedIds = if (selectedIds.size == allPhotos.size) {
                                emptySet()
                            } else {
                                allPhotos.map { it.id }.toSet()
                            }
                        }
                    ) {
                        Text(
                            text = if (selectedIds.size == allPhotos.size) "NONE" else "ALL",
                            color = VintageAmber,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ─── PHOTO GRID / EMPTY STATE ─────────────────────────────────────────────
            if (isLoading || isImporting) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = VintageAmber, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isImporting) "DEVELOPING FILM FRAME..." else "LOADING DARKROOM...",
                            color = VintageAmber,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (allPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dazz_darkroom_camera),
                            contentDescription = null,
                            tint = TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "NO PHOTOGRAPHS YET",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Shoot frames with Cameleo or tap the import button to develop your photos.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(allPhotos, key = { _, item -> item.id }) { index, item ->
                        val isSelected = selectedIds.contains(item.id)
                        DazzGalleryThumbnail(
                            item = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (isSelected) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                } else {
                                    selectedIndex = index
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedIds = setOf(item.id)
                                }
                            }
                        )
                    }
                }
            }
        }

        // ─── MULTI-SELECT BOTTOM DOCK (Simple, No Like) ──────────────────────────────
        AnimatedVisibility(
            visible = isSelectionMode && selectedIds.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color(0xEE161822),
                border = BorderStroke(1.dp, Color(0x44FFFFFF)),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "${selectedIds.size} Selected",
                        color = VintageAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(Color(0x33FFFFFF))
                    )
                    // Share
                    IconButton(
                        onClick = {
                            val selectedItems = allPhotos.filter { selectedIds.contains(it.id) }
                            sharePhotos(context, selectedItems)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dazz_share),
                            contentDescription = "Share Selected",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Delete
                    IconButton(
                        onClick = { showBatchDeleteConfirm = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dazz_delete),
                            contentDescription = "Delete Selected",
                            tint = VintageRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Batch delete confirmation
        if (showBatchDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                containerColor = Color(0xFF1B1D25),
                title = {
                    Text(
                        text = "DELETE ${selectedIds.size} PHOTOGRAPHS?",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "These photographs will be permanently deleted from your darkroom.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBatchDeleteConfirm = false
                            val toDelete = allPhotos.filter { selectedIds.contains(it.id) }
                            deletePhotos(context, toDelete.map { it.uri })
                            allPhotos = allPhotos.filter { !selectedIds.contains(it.id) }
                            selectedIds = emptySet()
                            isSelectionMode = false
                        }
                    ) {
                        Text("DELETE", color = VintageRed, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) {
                        Text("CANCEL", color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }

        // ─── FULL SCREEN PHOTO VIEWER OVERLAY ─────────────────────────────────────
        selectedIndex?.let { initialIndex ->
            DazzFullScreenViewer(
                photos = allPhotos,
                initialIndex = initialIndex,
                onDismiss = { selectedIndex = null },
                onDelete = { photoToDelete ->
                    onDeletePhoto(photoToDelete)
                },
                onShare = { photoToShare ->
                    sharePhoto(context, photoToShare)
                },
                onRedevelop = { photoToRedevelop ->
                    redevelopingItem = photoToRedevelop
                }
            )
        }

        // ─── REDEVELOP FILM SHEET ─────────────────────────────────────────────────
        redevelopingItem?.let { item ->
            RedevelopFilmSheet(
                item = item,
                isRedeveloping = isRedeveloping,
                onDismiss = { redevelopingItem = null },
                onConfirmRedevelop = { preset ->
                    coroutineScope.launch {
                        isRedeveloping = true
                        val newUri = redevelopPhoto(context, item, preset)
                        isRedeveloping = false
                        redevelopingItem = null
                        if (newUri != null) {
                            allPhotos = loadCameleoPhotos(context)
                            Toast.makeText(context, "Frame redeveloped with ${preset.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to redevelop photo", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

// ─── DAZZ CAM THUMBNAIL COMPONENT ───────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DazzGalleryThumbnail(
    item: GalleryItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(item.uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(item.uri) {
        withContext(Dispatchers.IO) {
            try {
                if (item.isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val bmp = context.contentResolver.loadThumbnail(item.uri, android.util.Size(512, 512), null)
                    bitmap = bmp.asImageBitmap()
                } else {
                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 4 // downscaled thumbnail for silky smooth scroll
                        }
                        val bmp = BitmapFactory.decodeStream(stream, null, options)
                        bmp?.let { bitmap = it.asImageBitmap() }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(SurfaceElevated)
            .then(
                if (isSelected) Modifier.border(2.dp, VintageAmber, RoundedCornerShape(2.dp)) else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = TextMuted)
        }

        // Selection Checkmark Overlay
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                if (isSelected) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_dazz_check_selected),
                        contentDescription = "Selected",
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_dazz_check_unselected),
                        contentDescription = "Unselected",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Video REC indicator
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = VintageAmber,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "REC",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Film emulsion badge in bottom-left
            val filmTag = remember(item.name, item.cameraModel) {
                resolveFilterName(item.name, item.cameraModel)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = filmTag.uppercase(),
                    color = Color(0xFFE2E4EA),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── DAZZ FULL SCREEN VIEWER WITH AUTHENTIC ACTION DOCK ─────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DazzFullScreenViewer(
    photos: List<GalleryItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDelete: (GalleryItem) -> Unit,
    onShare: (GalleryItem) -> Unit,
    onRedevelop: (GalleryItem) -> Unit
) {
    val context = LocalContext.current
    val safeInitial = initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { photos.size }
    )

    val currentPage = pagerState.currentPage.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    val currentItem = photos.getOrNull(currentPage)

    val filmTag = remember(currentItem?.name, currentItem?.cameraModel) {
        resolveFilterName(currentItem?.name, currentItem?.cameraModel)
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Horizontal Pager for swiping between photos
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> photos.getOrNull(index)?.id ?: index.toLong() }
        ) { page ->
            val item = photos.getOrNull(page)
            if (item != null) {
                DazzFullScreenPhotoPage(item = item)
            }
        }

        // ─── TOP TOOLBAR (Close, Counter, Film Tag) ──────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close / Back button (simple icon, NO bubble background)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_dazz_back),
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Dazz Cam DarkroomNumberLabel counter e.g. "3 / 12"
            if (photos.isNotEmpty()) {
                Text(
                    text = "${currentPage + 1} / ${photos.size}",
                    color = VintageAmber,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // Film / Camera Profile Tag (clean text, no bubble)
            Text(
                text = filmTag,
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // ─── BOTTOM ACTIONS (Share, Redevelop, Delete - Clean, No Bubbles) ───
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Filter Name Only (No 'Shot on Cameleo', clean typography)
            Text(
                text = filmTag.uppercase(),
                color = VintageAmber.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: Flat and clean, NO bubble/capsule border around Delete or other icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 1. Share
                IconButton(
                    onClick = { currentItem?.let { onShare(it) } },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_dazz_share),
                        contentDescription = "Share",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 2. Redevelop / Edit (Dazz Cam signature)
                IconButton(
                    onClick = { currentItem?.let { onRedevelop(it) } },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_dazz_redevelop),
                        contentDescription = "Redevelop Film",
                        tint = VintageAmber,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 3. Delete (Clean icon, NO bubble around it)
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_dazz_delete),
                        contentDescription = "Delete",
                        tint = VintageRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Single delete confirmation dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = Color(0xFF1B1D25),
                title = {
                    Text(
                        text = "DELETE PHOTOGRAPH?",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This photograph will be permanently removed from your darkroom album.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            currentItem?.let { onDelete(it) }
                        }
                    ) {
                        Text("DELETE", color = VintageRed, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("CANCEL", color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }
    }
}

// ─── FULL SCREEN PHOTO PAGE ─────────────────────────────────────────────────
@Composable
private fun DazzFullScreenPhotoPage(item: GalleryItem) {
    val context = LocalContext.current
    var fullBitmap by remember(item.uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isImageLoading by remember(item.uri) { mutableStateOf(true) }

    LaunchedEffect(item.uri) {
        withContext(Dispatchers.IO) {
            try {
                if (item.isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val bmp = context.contentResolver.loadThumbnail(item.uri, android.util.Size(1080, 1080), null)
                    fullBitmap = bmp.asImageBitmap()
                } else {
                    context.contentResolver.openInputStream(item.uri)?.use { boundsStream ->
                        val boundsOptions = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeStream(boundsStream, null, boundsOptions)
                        val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
                        var sample = 1
                        while (maxDim / sample > 2048) {
                            sample *= 2
                        }

                        context.contentResolver.openInputStream(item.uri)?.use { stream ->
                            val decodeOptions = BitmapFactory.Options().apply {
                                inSampleSize = sample
                                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                            }
                            val bmp = BitmapFactory.decodeStream(stream, null, decodeOptions)
                            bmp?.let { fullBitmap = it.asImageBitmap() }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                isImageLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 70.dp),
        contentAlignment = Alignment.Center
    ) {
        fullBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } ?: if (isImageLoading) {
            CircularProgressIndicator(color = VintageAmber, strokeWidth = 2.dp)
        } else {
            Text("Failed to load photograph", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }

        if (item.isVideo) {
            IconButton(
                onClick = { playVideo(context, item.uri) },
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f))
                    .border(2.dp, VintageAmber, CircleShape)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play Video",
                    tint = VintageAmber,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

// ─── REDEVELOP FILM MODAL SHEET ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RedevelopFilmSheet(
    item: GalleryItem,
    isRedeveloping: Boolean,
    onDismiss: () -> Unit,
    onConfirmRedevelop: (CameraPreset) -> Unit
) {
    val presets = remember {
        PresetCatalog.allPresets.filter { it.category != CameraCategory.CAMCORDER }
    }
    var selectedPreset by remember { mutableStateOf(presets.first()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14161E),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = Color(0x44FFFFFF)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "REDEVELOP FILM NEGATIVE",
                        color = VintageAmber,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Select an analog camera emulsion to re-render this frame",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_dazz_close),
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Presets Horizontal Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { preset ->
                    val isChosen = selectedPreset.id == preset.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isChosen) Color(0xFF2A2D39) else Color(0xFF1B1D25),
                        border = BorderStroke(
                            if (isChosen) 1.5.dp else 1.dp,
                            if (isChosen) VintageAmber else Color(0x22FFFFFF)
                        ),
                        modifier = Modifier
                            .width(130.dp)
                            .clickable { selectedPreset = preset }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = preset.name,
                                color = if (isChosen) VintageAmber else TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preset.subtitle,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Confirm Redevelop Button
            Button(
                onClick = { onConfirmRedevelop(selectedPreset) },
                enabled = !isRedeveloping,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VintageAmber,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isRedeveloping) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEVELOPING NEGATIVE...",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_dazz_redevelop),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEVELOP WITH ${selectedPreset.name.uppercase()}",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── PHOTO LOADING, PERSISTENCE & SYSTEM ACTIONS ────────────────────────────
private fun resolveFilterName(name: String?, cameraModel: String?): String {
    val cleanModel = cameraModel?.replace("CAMELEO", "", ignoreCase = true)?.trim()
    if (!cleanModel.isNullOrEmpty()) {
        if (cleanModel.equals("Natural", ignoreCase = true)) return "Zero"
        return cleanModel
    }
    val fileName = name ?: ""
    val matched = PresetCatalog.allPresets.firstOrNull { preset ->
        fileName.contains(preset.name.replace(" ", "_"), ignoreCase = true) ||
        fileName.contains(preset.name.replace(" ", ""), ignoreCase = true) ||
        fileName.contains(preset.id, ignoreCase = true)
    }
    return matched?.name ?: when {
        fileName.contains("FXNR", ignoreCase = true) -> "FXNR"
        fileName.contains("CLASSIC", ignoreCase = true) -> "D Classic"
        fileName.contains("GR", ignoreCase = true) -> "GR Digital"
        fileName.contains("S67", ignoreCase = true) -> "S Classic"
        fileName.contains("ZERO", ignoreCase = true) -> "Zero"
        fileName.contains("D3D", ignoreCase = true) -> "D3D"
        else -> "Zero"
    }
}

private suspend fun loadCameleoPhotos(context: Context): List<GalleryItem> = withContext(Dispatchers.IO) {
    val items = mutableListOf<GalleryItem>()
    val imgProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED
    )
    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
    val selectionArgs = arrayOf("CAMELEO_%", "CRAFTCAM_%")
    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

    // 1. Query Images
    try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imgProjection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val date = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                val cameraModel = try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        exif.getAttribute(ExifInterface.TAG_MODEL)
                            ?.replace("CAMELEO", "", ignoreCase = true)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                    }
                } catch (_: Exception) { null }

                items.add(GalleryItem(id, uri, name, date, cameraModel, isVideo = false))
            }
        }
    } catch (_: Exception) {}

    // 2. Query Videos
    try {
        val vidProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            vidProjection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val date = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                items.add(GalleryItem(id, uri, name, date, "Analog Camcorder", isVideo = true))
            }
        }
    } catch (_: Exception) {}

    items.sortedByDescending { it.dateAdded }
}

private fun deletePhoto(context: Context, uri: Uri) {
    try {
        context.contentResolver.delete(uri, null, null)
    } catch (_: Exception) {}
}

private fun deletePhotos(context: Context, uris: List<Uri>) {
    for (uri in uris) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {}
    }
}

private fun sharePhoto(context: Context, item: GalleryItem) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = if (item.isVideo) "video/mp4" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, if (item.isVideo) "Share Analog Video" else "Share Vintage Photograph"))
}

private fun sharePhotos(context: Context, items: List<GalleryItem>) {
    if (items.isEmpty()) return
    if (items.size == 1) {
        sharePhoto(context, items.first())
        return
    }
    val uris = ArrayList(items.map { it.uri })
    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share ${items.size} Photographs"))
}

private fun playVideo(context: Context, uri: Uri) {
    try {
        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(playIntent)
    } catch (_: Exception) {}
}

private suspend fun redevelopPhoto(
    context: Context,
    item: GalleryItem,
    preset: CameraPreset
): Uri? = withContext(Dispatchers.IO) {
    try {
        val bitmap = context.contentResolver.openInputStream(item.uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return@withContext null

        val redeveloped = ImageProcessor.applyPresetEffects(
            context = context,
            source = bitmap,
            preset = preset
        )
        ImageProcessor.saveToGallery(context, redeveloped, preset.name)
    } catch (e: Exception) {
        null
    }
}

private suspend fun importAndDevelopPhoto(
    context: Context,
    uri: Uri,
    preset: CameraPreset
): Uri? = withContext(Dispatchers.IO) {
    try {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return@withContext null

        val developed = ImageProcessor.applyPresetEffects(
            context = context,
            source = bitmap,
            preset = preset
        )
        ImageProcessor.saveToGallery(context, developed, preset.name)
    } catch (e: Exception) {
        null
    }
}
