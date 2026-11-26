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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
    onNavigateBack: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var allPhotos by remember { mutableStateOf<List<GalleryItem>>(GalleryCache.cachedItems ?: emptyList()) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(GalleryCache.cachedItems == null) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // Redevelop state
    var redevelopingItem by remember { mutableStateOf<GalleryItem?>(null) }
    var isRedeveloping by remember { mutableStateOf(false) }

    // Import Photo state
    var isImporting by remember { mutableStateOf(false) }

    // Refresh photos non-blocking in background
    LaunchedEffect(Unit) {
        val fresh = withContext(Dispatchers.IO) { GalleryCache.queryCameleoMedia(context) }
        allPhotos = fresh
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
                        GalleryCache.invalidate()
                        allPhotos = withContext(Dispatchers.IO) { GalleryCache.queryCameleoMedia(context) }
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


    val prefs = remember { context.getSharedPreferences("gallery", Context.MODE_PRIVATE) }
    var favourites by remember { mutableStateOf(prefs.getStringSet("favourites", emptySet()).orEmpty().toSet()) }
    var negatives by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(allPhotos) {
        negatives = withContext(Dispatchers.IO) { java.io.File(context.filesDir, "negatives").list().orEmpty().toSet() }
    }
    var album by remember { mutableStateOf("All Photos") }
    var expanded by remember { mutableStateOf(false) }
    val albums = remember(allPhotos, favourites, negatives) {
        linkedMapOf("All Photos" to allPhotos,
            "Favourites" to allPhotos.filter { it.uri.toString() in favourites },
            "Negatives" to allPhotos.filter { !it.isVideo && "neg_${it.id}.jpg" in negatives }
        ).apply { putAll(allPhotos.groupBy {
            if (it.name.startsWith("CAMELEO_3D_")) "D3D" else resolveFilterName(it.name, it.cameraModel)
        }.toSortedMap()) }
    }
    val albumItems = albums[album].orEmpty()
    val visible = remember(albumItems) {
        albumItems.sortedWith(compareBy<GalleryItem> { it.dateAdded }.thenBy { it.id })
    }
    // Delete single photo
    val onDeletePhoto: (GalleryItem) -> Unit = { item ->
        deletePhoto(context, item.uri)
        GalleryCache.remove(item.id)
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
            Box(Modifier.weight(1f)) {
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
            } else if (visible.isEmpty()) {
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
                            text = "No photos in this album",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Take a photo or open the album menu to import one.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                key(album) {
                    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = visible.lastIndex)
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (isSelectionMode) 0.dp else 108.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        itemsIndexed(visible, key = { _, item -> item.uri.toString() }) { index, item ->
                            val isSelected = selectedIds.contains(item.uri)
                            DazzGalleryThumbnail(
                                item = item,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) {
                                            selectedIds - item.uri
                                        } else {
                                            selectedIds + item.uri
                                        }
                                    } else {
                                        selectedIndex = index
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds = setOf(item.uri)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            }
            if (isSelectionMode) {
                Box(Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().height(124.dp)) {
                    Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${selectedIds.size} selected", color = TextSecondary, fontSize = 14.sp)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 24.dp, end = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            val enabled = selectedIds.isNotEmpty()
                            GalleryAction(R.drawable.ic_dazz_share, "Share selected", {
                                sharePhotos(context, visible.filter { it.uri in selectedIds })
                            }, enabled = enabled)
                            GalleryAction(R.drawable.ic_dazz_liked, "Toggle favourites", {
                                val keys = selectedIds.map { it.toString() }.toSet()
                                favourites = if (favourites.containsAll(keys)) favourites - keys else favourites + keys
                                prefs.edit().putStringSet("favourites", favourites).apply()
                                selectedIds = emptySet()
                            }, enabled = enabled)
                            GalleryAction(R.drawable.ic_dazz_delete, "Delete selected", { showBatchDeleteConfirm = true }, enabled = enabled)
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).navigationBarsPadding()
            .padding(start = 26.dp, bottom = if (isSelectionMode) 76.dp else 24.dp)) {
            GalleryAction(R.drawable.ic_dazz_camera_back, "Return to camera", onNavigateBack, large = true)
        }
        BackHandler(enabled = expanded || selectedIndex != null || isSelectionMode) {
            when {
                expanded -> expanded = false
                selectedIndex != null -> selectedIndex = null
                else -> { isSelectionMode = false; selectedIds = emptySet() }
            }
        }
        val categories = listOf("All Photos", "Favourites", "Negatives")
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GalleryAction(R.drawable.ic_dazz_setting, "Settings", onSettings)
            if (!expanded) {
                Button(onClick = { expanded = true }, shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = galleryChrome),
                    contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(40.dp)) {
                    if (album in categories) Text(album, color = Color.White, fontSize = 14.sp)
                    else GalleryCameraIcon(album, Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Icon(painterResource(R.drawable.gallery_menu_chevron), "Choose album", Modifier.size(13.dp), tint = Color.White)
                }
            }
            GalleryAction(if (isSelectionMode) R.drawable.ic_dazz_close else R.drawable.ic_dazz_select,
                if (isSelectionMode) "Cancel selection" else "Select photos",
                { isSelectionMode = !isSelectionMode; selectedIds = emptySet() })
        }
        if (expanded) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null) { expanded = false })
                Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp)
                    .fillMaxWidth(0.765f).widthIn(max = 340.dp).heightIn(max = maxHeight * 0.54f)
                    .clip(RoundedCornerShape(22.dp)).background(galleryChrome)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                    .verticalScroll(rememberScrollState()).padding(vertical = 10.dp)) {
                    albums.forEach { (name, entries) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .selectable(album == name, role = Role.RadioButton) {
                                album = name; expanded = false; selectedIds = emptySet()
                            }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                                val icon = when (name) {
                                    "All Photos" -> R.drawable.ic_dazz_cate_all
                                    "Favourites" -> R.drawable.ic_dazz_cate_liked
                                    "Negatives" -> R.drawable.ic_dazz_cate_negatives
                                    else -> null
                                }
                                if (icon != null) Icon(painterResource(icon), null, Modifier.size(25.dp), tint = Color.White)
                                else GalleryCameraIcon(name, Modifier.size(36.dp), decorative = true)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(name, Modifier.weight(1f), color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entries.size.toString(), color = Color.White.copy(alpha = 0.64f), fontSize = 14.sp)
                        }
                    }
                    TextButton(onClick = {
                        expanded = false
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_dazz_importphoto), null, Modifier.size(24.dp), tint = Color.White)
                        Text("Import photo", color = Color.White)
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
                            val toDelete = allPhotos.filter { selectedIds.contains(it.uri) }
                            deletePhotos(context, toDelete.map { it.uri })
                            toDelete.forEach { GalleryCache.remove(it.id) }
                            val updated = allPhotos.filter { !selectedIds.contains(it.uri) }
                            allPhotos = updated
                            GalleryCache.cachedItems = updated
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
        selectedIndex?.takeIf { visible.isNotEmpty() }?.let { initialIndex ->
            DazzFullScreenViewer(
                photos = visible,
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
                            GalleryCache.invalidate()
                            allPhotos = withContext(Dispatchers.IO) { GalleryCache.queryCameleoMedia(context) }
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

private val galleryChrome = Color(0xED30231D)

@Composable
private fun GalleryAction(icon: Int, label: String, action: () -> Unit, large: Boolean = false, enabled: Boolean = true) {
    val background = if (large) Color(0xFFDBD6D2) else galleryChrome
    val foreground = if (large) Color.Black else Color.White
    IconButton(onClick = action, enabled = enabled, modifier = Modifier.size(if (large) 48.dp else 44.dp)) {
        Box(Modifier.size(if (large) 44.dp else 40.dp).background(background, CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), label, Modifier.size(if (large) 22.dp else 20.dp),
                tint = foreground.copy(alpha = if (enabled) 1f else 0.38f))
        }
    }
}

@Composable
private fun GalleryCameraIcon(name: String, modifier: Modifier = Modifier, decorative: Boolean = false) {
    val context = LocalContext.current
    val id = remember(name) {
        val preset = PresetCatalog.allPresets.firstOrNull { it.name.equals(name, ignoreCase = true) }
        preset?.let { context.resources.getIdentifier(it.iconResName, "drawable", context.packageName) }
            ?.takeIf { it != 0 } ?: R.drawable.cam_original
    }
    Image(painterResource(id), if (decorative) null else name, modifier, contentScale = ContentScale.Fit)
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
    var bitmap by remember(item.id) {
        mutableStateOf(GalleryCache.getThumbnail(item.id))
    }

    LaunchedEffect(item.id, item.uri) {
        if (bitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(item.uri, android.util.Size(256, 256), null)
                } else {
                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 8
                        }
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                }
                bmp?.asImageBitmap()?.also { ib ->
                    GalleryCache.putThumbnail(item.id, ib)
                    bitmap = ib
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
                if (item.isVideo) {
                    if (page == currentPage) key(item.uri) { GalleryVideoPage(item) }
                } else {
                    DazzFullScreenPhotoPage(item = item, active = page == currentPage)
                }
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
            GalleryAction(R.drawable.ic_dazz_back, "Back to gallery", onDismiss)
            Surface(shape = CircleShape, color = galleryChrome) {
                Text("${currentPage + 1} / ${photos.size}", color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                GalleryCameraIcon(filmTag, Modifier.size(28.dp))
            }
        }

        // ─── BOTTOM ACTIONS (Share, Redevelop, Delete - Clean, No Bubbles) ───
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
private fun DazzFullScreenPhotoPage(item: GalleryItem, active: Boolean) {
    val context = LocalContext.current
    var fullBitmap by remember(item.uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isImageLoading by remember(item.uri) { mutableStateOf(true) }

    LaunchedEffect(item.uri) {
        withContext(Dispatchers.IO) {
            try {
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
        fullBitmap?.let { bitmap ->
            var viewport by remember { mutableStateOf(IntSize.Zero) }
            var zoom by remember(item.uri, active, viewport) { mutableFloatStateOf(1f) }
            var pan by remember(item.uri, active, viewport) { mutableStateOf(Offset.Zero) }

            fun transform(target: Float, focus: Offset, movement: Offset) {
                if (viewport.width == 0 || viewport.height == 0) return
                val next = target.coerceIn(1f, 4f)
                val center = Offset(viewport.width / 2f, viewport.height / 2f)
                val anchor = focus - center
                val offset = anchor + (pan - anchor) * (next / zoom) + movement
                val fit = minOf(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
                val limitX = ((bitmap.width * fit * next - viewport.width) / 2f).coerceAtLeast(0f)
                val limitY = ((bitmap.height * fit * next - viewport.height) / 2f).coerceAtLeast(0f)
                pan = Offset(offset.x.coerceIn(-limitX, limitX), offset.y.coerceIn(-limitY, limitY))
                zoom = next
            }

            Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }
                .pointerInput(item.uri, active, viewport) {
                    detectTapGestures(onDoubleTap = { focus ->
                        transform(if (zoom > 1f) 1f else 2f, focus, Offset.Zero)
                    })
                }
                .pointerInput(item.uri, active, viewport) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var transforming = false
                        var totalPan = Offset.Zero
                        do {
                            val event = awaitPointerEvent()
                            val movement = event.calculatePan()
                            totalPan += movement
                            if (event.changes.count { it.pressed } >= 2 ||
                                (zoom > 1f && totalPan.getDistance() > viewConfiguration.touchSlop)) {
                                transforming = true
                            }
                            if (transforming && event.changes.any { it.pressed && it.previousPressed }) {
                                transform(zoom * event.calculateZoom(), event.calculateCentroid(useCurrent = false), movement)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            ) {
                Image(bitmap, item.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    })
            }
        } ?: if (isImageLoading) {
            CircularProgressIndicator(color = VintageAmber, strokeWidth = 2.dp)
        } else {
            Text("Failed to load photograph", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
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
                            .width(100.dp)
                            .clickable { selectedPreset = preset }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            GalleryCameraIcon(preset.name, Modifier.size(36.dp))
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
    if (name?.startsWith("CAMELEO_3D_") == true) return "D3D"
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
    GalleryCache.queryCameleoMedia(context)
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

private suspend fun redevelopPhoto(
    context: Context,
    item: GalleryItem,
    preset: CameraPreset
): Uri? = withContext(Dispatchers.IO) {
    try {
        val bitmap = context.contentResolver.openInputStream(item.uri)?.use { stream ->
            val options = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return@withContext null

        try {
            ImageProcessor.developBitmap(
                context = context,
                sourceBitmap = bitmap,
                preset = preset
            )
        } finally {
            try { bitmap.recycle() } catch (_: Throwable) {}
        }
    } catch (e: Exception) {
        android.util.Log.e("GalleryScreen", "Failed to redevelop photo", e)
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
            val options = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return@withContext null

        try {
            ImageProcessor.developBitmap(
                context = context,
                sourceBitmap = bitmap,
                preset = preset
            )
        } finally {
            try { bitmap.recycle() } catch (_: Throwable) {}
        }
    } catch (e: Exception) {
        android.util.Log.e("GalleryScreen", "Failed to import photo", e)
        null
    }
}
