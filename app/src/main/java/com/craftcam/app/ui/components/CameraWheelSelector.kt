package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.ui.theme.*

@Composable
fun CameraWheelSelector(
    modifier: Modifier = Modifier,
    presets: List<CameraPreset>,
    selectedPreset: CameraPreset,
    onPresetSelected: (CameraPreset) -> Unit,
    onOpenCollection: () -> Unit
) {
    val listState = rememberLazyListState()
    val view = LocalView.current

    // Automatically scroll to selected preset on launch
    LaunchedEffect(selectedPreset) {
        val index = presets.indexOf(selectedPreset)
        if (index >= 0) {
            listState.animateScrollToItem(index.coerceAtLeast(0))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(presets, key = { it.id }) { preset ->
                val isSelected = preset.id == selectedPreset.id
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) SurfaceCard else SurfaceGraphite,
                    label = "bg"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) VintageAmber else Color.Transparent,
                    label = "border"
                )
                val context = LocalContext.current
                val iconResId = remember(preset.iconResName) {
                    context.resources.getIdentifier(preset.iconResName, "drawable", context.packageName)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(backgroundColor)
                        .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onPresetSelected(preset)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.widthIn(min = 68.dp)
                    ) {
                        if (iconResId != 0) {
                            Image(
                                painter = painterResource(id = iconResId),
                                contentDescription = preset.name,
                                modifier = Modifier
                                    .size(38.dp)
                                    .padding(bottom = 4.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Text(
                            text = preset.year,
                            color = if (isSelected) VintageAmber else TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = preset.name,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Button to open full collection browser
        IconButton(
            onClick = onOpenCollection,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceElevated)
        ) {
            Icon(
                Icons.Default.GridView,
                contentDescription = "Collection",
                tint = VintageAmber
            )
        }
    }
}

