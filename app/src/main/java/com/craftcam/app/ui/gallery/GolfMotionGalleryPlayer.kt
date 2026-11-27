package com.craftcam.app.ui.gallery

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import com.craftcam.app.motion.GolfMotionConfig
import com.craftcam.app.motion.GolfMotionDuration
import com.craftcam.app.motion.GolfMotionMetadata
import com.craftcam.app.motion.GolfMotionProcessor
import com.craftcam.app.motion.GolfMotionStore
import com.craftcam.app.motion.GolfPlaybackCadence
import com.craftcam.app.motion.MotionPhotoImporter
import com.craftcam.app.ui.theme.SurfaceElevated
import com.craftcam.app.ui.theme.TextMuted
import com.craftcam.app.ui.theme.TextPrimary
import com.craftcam.app.ui.theme.TextSecondary
import com.craftcam.app.ui.theme.VintageAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Gallery page for a single logical Golf Motion Photo (`item.hasMotionClip == true`).
 *
 * Defaults to the preserved still photograph as its cover, shows a compact Motion badge,
 * supports tap/press or toggle pill playback with looping + lightweight progress indicator,
 * and allows choosing an updated cover frame without duplicating MediaStore entries.
 */
@Composable
internal fun GolfMotionGalleryPage(
    item: GalleryItem,
    active: Boolean,
    onActiveExtractTimeChanged: (Long) -> Unit,
    onCoverChanged: () -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val clipFile = remember(item.id) { GolfMotionStore.videoFile(context, item.id) }
    var metadata by remember(item.id) { mutableStateOf<GolfMotionMetadata?>(null) }
    var coverOverrideBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }

    var isMotionView by remember(item.id) { mutableStateOf(false) }
    var isPlaying by remember(item.id) { mutableStateOf(false) }
    var loopPlayback by remember(item.id) { mutableStateOf(true) }
    var isMuted by remember(item.id) { mutableStateOf(false) }
    var playbackProgress by remember(item.id) { mutableFloatStateOf(0f) }
    var isPickingCover by remember(item.id) { mutableStateOf(false) }

    // Cover scrubber state
    var scrubberIndex by remember(item.id) { mutableIntStateOf(0) }
    var coverPreviewBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember(item.id) { mutableStateOf(false) }

    var mediaPlayerRef by remember(item.id) { mutableStateOf<MediaPlayer?>(null) }
    var videoViewRef by remember(item.id) { mutableStateOf<VideoView?>(null) }
    var videoAspectRatio by remember(item.id) { mutableFloatStateOf(3f / 4f) }

    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) {
            val loadedMeta = GolfMotionStore.loadMetadata(context, item.id)
            val override = GolfMotionStore.loadCoverOverride(context, item.id)
            metadata = loadedMeta
            coverOverrideBitmap = override
            if (loadedMeta != null) {
                loopPlayback = loadedMeta.loopPlayback
                isMuted = !loadedMeta.hasAudio
                scrubberIndex = loadedMeta.coverFrameIndex
                onActiveExtractTimeChanged(loadedMeta.coverTimeMs())
                if (loadedMeta.width > 0 && loadedMeta.height > 0) {
                    videoAspectRatio = loadedMeta.width.toFloat() / loadedMeta.height.toFloat()
                }
            }
        }
    }

    // Pause motion playback smoothly if pager moves away
    LaunchedEffect(active) {
        if (!active) {
            isMotionView = false
            isPlaying = false
            isPickingCover = false
            playbackProgress = 0f
            try {
                videoViewRef?.pause()
            } catch (_: Throwable) {}
        }
    }

    // Apply live loop and mute changes to active MediaPlayer
    LaunchedEffect(loopPlayback, isMuted, mediaPlayerRef) {
        mediaPlayerRef?.let { mp ->
            try {
                mp.isLooping = loopPlayback
                val vol = if (isMuted) 0f else 1f
                mp.setVolume(vol, vol)
            } catch (_: Throwable) {}
        }
    }

    // Lightweight playback progress sampler
    LaunchedEffect(isMotionView, isPlaying, active) {
        while (isActive && isMotionView && isPlaying && active) {
            val view = videoViewRef
            if (view != null) {
                val dur = view.duration
                val pos = view.currentPosition
                if (dur > 0) {
                    playbackProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    onActiveExtractTimeChanged(pos.toLong())
                }
            }
            delay(32L)
        }
    }

    // Load cover preview frame while scrubbing
    LaunchedEffect(isPickingCover, scrubberIndex, metadata) {
        if (!isPickingCover) return@LaunchedEffect
        val meta = metadata ?: return@LaunchedEffect
        val total = meta.totalFrames.coerceAtLeast(1)
        val clamped = scrubberIndex.coerceIn(0, total - 1)
        val targetMs = if (total > 1) {
            ((clamped.toFloat() / (total - 1)) * meta.durationMs).roundToInt().toLong()
        } else 0L
        onActiveExtractTimeChanged(targetMs)
        isLoadingPreview = true
        val decoded = withContext(Dispatchers.IO) {
            GolfMotionProcessor.extractFrameAt(context, clipFile, targetMs)
        }
        coverPreviewBitmap = decoded
        isLoadingPreview = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isPickingCover) {
            // Cover Frame Selector mode
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 84.dp),
                contentAlignment = Alignment.Center
            ) {
                coverPreviewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Selected cover frame",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: if (isLoadingPreview) {
                    CircularProgressIndicator(color = VintageAmber, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "PREVIEW UNAVAILABLE",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else if (isMotionView && clipFile.exists() && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && active) {
            // Motion Clip Playback mode
            var isPrepared by remember(item.id) { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 70.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(videoAspectRatio.coerceIn(0.4f, 2.5f))
                        .clickable {
                            isPlaying = !isPlaying
                            videoViewRef?.let { v ->
                                if (isPlaying) v.start() else v.pause()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setOnPreparedListener { mp ->
                                    mediaPlayerRef = mp
                                    mp.isLooping = loopPlayback
                                    val vol = if (isMuted) 0f else 1f
                                    mp.setVolume(vol, vol)
                                    if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                        videoAspectRatio = mp.videoWidth.toFloat() / mp.videoHeight.toFloat()
                                    }
                                    isPrepared = true
                                    if (isPlaying) start()
                                }
                                setOnCompletionListener {
                                    if (!loopPlayback) {
                                        isPlaying = false
                                        playbackProgress = 1f
                                    }
                                }
                                setOnErrorListener { _, _, _ ->
                                    isMotionView = false
                                    isPlaying = false
                                    true
                                }
                                setVideoPath(clipFile.absolutePath)
                                videoViewRef = this
                            }
                        },
                        onRelease = { view ->
                            mediaPlayerRef = null
                            videoViewRef = null
                            try {
                                view.stopPlayback()
                            } catch (_: Throwable) {}
                        },
                        update = { view ->
                            if (isPrepared) {
                                if (isPlaying && !view.isPlaying) {
                                    view.start()
                                } else if (!isPlaying && view.isPlaying) {
                                    view.pause()
                                }
                            }
                        }
                    )

                    if (!isPrepared) {
                        CircularProgressIndicator(color = VintageAmber, strokeWidth = 2.dp)
                    } else if (!isPlaying) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xB8111216),
                            border = BorderStroke(1.dp, VintageAmber.copy(alpha = 0.7f)),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play motion",
                                    tint = VintageAmber,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }

                    // Hairline playback progress indicator
                    LinearProgressIndicator(
                        progress = { playbackProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp),
                        color = VintageAmber,
                        trackColor = Color.White.copy(alpha = 0.22f)
                    )
                }
            }
        } else {
            // Default Still Cover View (tap or press-and-hold plays motion)
            DazzFullScreenPhotoPage(
                item = item,
                active = active,
                overrideBitmap = coverOverrideBitmap,
                onTapMedia = {
                    if (clipFile.exists()) {
                        isMotionView = true
                        isPlaying = true
                    }
                },
                onLongPressMedia = {
                    if (clipFile.exists()) {
                        isMotionView = true
                        isPlaying = true
                    }
                }
            )
        }

        // Top-left compact MOTION badge / duration pill (non-obstructive below top toolbar)
        val meta = metadata
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 18.dp, top = 66.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isMotionView) VintageAmber.copy(alpha = 0.92f) else Color(0xB817181D),
                border = BorderStroke(
                    1.dp,
                    if (isMotionView) VintageAmber else Color.White.copy(alpha = 0.22f)
                ),
                modifier = Modifier.clickable {
                    if (!clipFile.exists()) return@clickable
                    isPickingCover = false
                    if (isMotionView) {
                        isMotionView = false
                        isPlaying = false
                        playbackProgress = 0f
                        meta?.let { onActiveExtractTimeChanged(it.coverTimeMs()) }
                    } else {
                        isMotionView = true
                        isPlaying = true
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (isMotionView && isPlaying) Icons.Default.Pause else Icons.Default.MotionPhotosOn,
                        contentDescription = null,
                        tint = if (isMotionView) Color.Black else VintageAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = buildString {
                            append(if (isMotionView) "MOTION PLAYING" else "MOTION")
                            if (meta != null) append(" • ${meta.durationMs / 1000f}s".replace(".0s", "s"))
                        },
                        color = if (isMotionView) Color.Black else TextPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Inline Controls above bottom action dock (Still / Motion switcher + Cover Frame picker + Loop/Audio)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 86.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isPickingCover && meta != null) {
                val maxIndex = (meta.totalFrames - 1).coerceAtLeast(1)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xE018191F),
                    border = BorderStroke(1.dp, VintageAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COVER FRAME ${(scrubberIndex + 1).coerceIn(1, meta.totalFrames.coerceAtLeast(1))} / ${meta.totalFrames.coerceAtLeast(1)}",
                                color = VintageAmber,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { isPickingCover = false },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("CANCEL", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                                Button(
                                    onClick = {
                                        val chosenBmp = coverPreviewBitmap
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                GolfMotionStore.updateCoverSelection(
                                                    context = context,
                                                    mediaId = item.id,
                                                    coverFrameIndex = scrubberIndex,
                                                    coverBitmap = chosenBmp
                                                )
                                                if (chosenBmp != null) {
                                                    GalleryCache.putThumbnail(item.id, GalleryCache.createThumbnail(chosenBmp, 256))
                                                }
                                            }
                                            metadata = GolfMotionStore.loadMetadata(context, item.id)
                                            coverOverrideBitmap = chosenBmp
                                            isPickingCover = false
                                            onCoverChanged()
                                            Toast.makeText(context, "Cover frame updated", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = VintageAmber, contentColor = Color.Black),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("SET COVER", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Slider(
                            value = scrubberIndex.toFloat().coerceIn(0f, maxIndex.toFloat()),
                            onValueChange = { scrubberIndex = it.roundToInt().coerceIn(0, maxIndex) },
                            valueRange = 0f..maxIndex.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = VintageAmber,
                                activeTrackColor = VintageAmber,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            )
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Still / Motion switcher pill
                Surface(
                    shape = CircleShape,
                    color = Color(0xCC18191F),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        SwitcherSegment(
                            label = "STILL",
                            selected = !isMotionView && !isPickingCover,
                            onClick = {
                                isPickingCover = false
                                isMotionView = false
                                isPlaying = false
                                meta?.let { onActiveExtractTimeChanged(it.coverTimeMs()) }
                            }
                        )
                        SwitcherSegment(
                            label = "MOTION",
                            selected = isMotionView && !isPickingCover,
                            onClick = {
                                if (clipFile.exists()) {
                                    isPickingCover = false
                                    isMotionView = true
                                    isPlaying = true
                                }
                            }
                        )
                    }
                }

                // Loop toggle (in Motion mode)
                if (isMotionView && !isPickingCover) {
                    SmallViewerPill(
                        icon = Icons.Default.Repeat,
                        label = if (loopPlayback) "LOOP" else "1X",
                        active = loopPlayback,
                        onClick = { loopPlayback = !loopPlayback }
                    )
                    if (meta?.hasAudio == true) {
                        SmallViewerPill(
                            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            label = if (isMuted) "MUTE" else "AUDIO",
                            active = !isMuted,
                            onClick = { isMuted = !isMuted }
                        )
                    }
                }

                // Cover frame selector button
                if ((meta?.totalFrames ?: 0) > 1) {
                    SmallViewerPill(
                        icon = Icons.Default.Tune,
                        label = "COVER",
                        active = isPickingCover,
                        onClick = {
                            isMotionView = false
                            isPlaying = false
                            isPickingCover = !isPickingCover
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitcherSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) VintageAmber else Color.Transparent,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SmallViewerPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (active) Color(0xD82A251B) else Color(0xCC18191F),
        border = BorderStroke(1.dp, if (active) VintageAmber else Color.White.copy(alpha = 0.16f)),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) VintageAmber else TextSecondary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                color = if (active) VintageAmber else TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Multi-format export / share dialog for Golf Motion Photos.
 */
@Composable
internal fun GolfMotionExportDialog(
    item: GalleryItem,
    extractTimeMs: Long,
    onDismiss: () -> Unit,
    onGalleryRefreshNeeded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        containerColor = Color(0xFF1B1D25),
        title = {
            Text(
                text = "GOLF MOTION EXPORT & SHARE",
                color = TextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isWorking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(color = VintageAmber, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Text("PREPARING EXPORT...", color = VintageAmber, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    ExportOptionRow(
                        icon = Icons.Default.Image,
                        title = "Share Original Photograph (JPEG)",
                        subtitle = "Unmodified Golf still photograph",
                        onClick = {
                            onDismiss()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Golf Photograph"))
                        }
                    )
                    ExportOptionRow(
                        icon = Icons.Default.Share,
                        title = "Share Motion Clip (MP4)",
                        subtitle = "Send clip to WhatsApp, Instagram, or apps",
                        onClick = {
                            val clipFile = GolfMotionStore.videoFile(context, item.id)
                            val shareUri = GolfMotionProcessor.getShareableFileUri(context, clipFile)
                            if (shareUri != null) {
                                onDismiss()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Golf Motion Clip"))
                            } else {
                                Toast.makeText(context, "Motion clip unavailable", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    ExportOptionRow(
                        icon = Icons.Default.VideoFile,
                        title = "Save Motion Video (MP4)",
                        subtitle = "Export to Movies/Cameleo album",
                        onClick = {
                            scope.launch {
                                isWorking = true
                                val saved = GolfMotionProcessor.exportMotionMp4ToMediaStore(context, item.id)
                                isWorking = false
                                onDismiss()
                                Toast.makeText(
                                    context,
                                    if (saved != null) "Saved MP4 to Movies/Cameleo" else "MP4 export failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    ExportOptionRow(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Extract Still Frame (JPEG)",
                        subtitle = "Save selected frame into Gallery",
                        onClick = {
                            scope.launch {
                                isWorking = true
                                val saved = GolfMotionProcessor.extractStillFrameToMediaStore(context, item.id, extractTimeMs)
                                isWorking = false
                                onDismiss()
                                if (saved != null) onGalleryRefreshNeeded()
                                Toast.makeText(
                                    context,
                                    if (saved != null) "Still frame saved to Gallery" else "Still frame extraction failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    ExportOptionRow(
                        icon = Icons.Default.GifBox,
                        title = "Export Animated GIF",
                        subtitle = "Retro looping GIF saved to Pictures/Cameleo",
                        onClick = {
                            scope.launch {
                                isWorking = true
                                val meta = withContext(Dispatchers.IO) { GolfMotionStore.loadMetadata(context, item.id) }
                                val saved = GolfMotionProcessor.exportMotionGifToMediaStore(
                                    context = context,
                                    mediaId = item.id,
                                    loop = meta?.loopPlayback ?: true
                                )
                                isWorking = false
                                onDismiss()
                                Toast.makeText(
                                    context,
                                    if (saved != null) "Saved GIF to Pictures/Cameleo" else "GIF export failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!isWorking) {
                TextButton(onClick = onDismiss) {
                    Text("CLOSE", color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
            }
        }
    )
}

@Composable
private fun ExportOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = VintageAmber, modifier = Modifier.size(20.dp))
            Column {
                Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}

/**
 * Dialog shown when an imported JPEG, Live Photo pair, or short MP4/MOV clip contains valid motion data.
 */
@Composable
internal fun GolfMotionImportDialog(
    probe: MotionPhotoImporter.ImportProbeResult.MotionAvailable,
    initialConfig: GolfMotionConfig,
    onDismiss: () -> Unit,
    onDevelopMotion: (GolfMotionConfig) -> Unit,
    onDevelopStillOnly: (() -> Unit)?
) {
    var config by remember(probe) { mutableStateOf(initialConfig) }
    val sourceLabel = when (probe.sourceKind) {
        "samsung_motion_photo" -> "Samsung Motion Photo"
        "google_motion_photo" -> "Google / Android Motion Photo"
        "apple_live_photo" -> "Apple Live Photo"
        "video_clip" -> "Short Video Clip"
        else -> "Embedded Motion Photo"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B1D25),
        title = {
            Text(
                text = "GOLF MOTION IMPORT",
                color = VintageAmber,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Detected: $sourceLabel",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Develop clip with Golf color science, grain, border, and temporal cadence.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Text("CLIP DURATION", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GolfMotionDuration.entries.forEach { d ->
                        val selected = config.duration == d
                        ImportChip(d.label, selected) { config = config.copy(duration = d) }
                    }
                }

                Text("PLAYBACK CADENCE", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GolfPlaybackCadence.entries.forEach { p ->
                        val selected = config.playback == p
                        ImportChip("${p.label} (${p.targetFps}fps)", selected) { config = config.copy(playback = p) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImportChip(
                        label = if (config.flashEffectEnabled) "Flash FX: ON" else "Flash FX: OFF",
                        selected = config.flashEffectEnabled
                    ) { config = config.copy(flashEffectEnabled = !config.flashEffectEnabled) }
                    ImportChip(
                        label = if (config.audioEnabled) "Audio: ON" else "Audio: OFF",
                        selected = config.audioEnabled
                    ) { config = config.copy(audioEnabled = !config.audioEnabled) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDevelopMotion(config) },
                colors = ButtonDefaults.buttonColors(containerColor = VintageAmber, contentColor = Color.Black)
            ) {
                Text("DEVELOP MOTION", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDevelopStillOnly != null) {
                    TextButton(onClick = onDevelopStillOnly) {
                        Text("STILL ONLY", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    )
}

@Composable
private fun ImportChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) VintageAmber else SurfaceElevated,
        border = BorderStroke(1.dp, if (selected) VintageAmber else Color.White.copy(alpha = 0.14f)),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else TextPrimary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}
