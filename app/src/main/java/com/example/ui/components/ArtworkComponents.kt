package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import java.io.File

@Composable
fun TrackArtwork(
    coverPath: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    contentDescription: String? = stringResource(R.string.cd_album_art),
) {
    val shape = RoundedCornerShape(cornerRadius)
    val validFile = remember(coverPath) {
        coverPath?.let { File(it) }?.takeIf { it.exists() }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (validFile != null) {
            AsyncImage(
                model = validFile,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(id = R.drawable.img_default_cover_1795379686089),
                placeholder = painterResource(id = R.drawable.img_default_cover_1795379686089),
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_default_cover_1795379686089),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun RotatingVinylDisc(
    coverPath: String?,
    isPlaying: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                val start = rotation.value % 360f
                rotation.snapTo(start)
                rotation.animateTo(
                    targetValue = start + 360f,
                    animationSpec = tween(durationMillis = 16000, easing = LinearEasing),
                )
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Subtle outer studio glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (isPlaying) 0.28f else 0.12f),
                            Color.Transparent,
                        )
                    ),
                    shape = CircleShape,
                )
        )

        // Outer vinyl ring
        Box(
            modifier = Modifier
                .fillMaxSize(0.90f)
                .shadow(elevation = 18.dp, shape = CircleShape, spotColor = primaryColor)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF141822),
                            Color(0xFF282F40),
                            Color(0xFF141822),
                            Color(0xFF232938),
                            Color(0xFF141822),
                        )
                    )
                )
                .border(1.5.dp, primaryColor.copy(alpha = 0.35f), CircleShape)
                .rotate(rotation.value),
            contentAlignment = Alignment.Center,
        ) {
            // Artwork inner disc
            TrackArtwork(
                coverPath = coverPath,
                cornerRadius = size,
                modifier = Modifier
                    .fillMaxSize(0.76f)
                    .clip(CircleShape)
                    .border(2.dp, primaryColor.copy(alpha = 0.45f), CircleShape),
            )

            // Center spindle hole
            Box(
                modifier = Modifier
                    .size(size * 0.11f)
                    .clip(CircleShape)
                    .background(surfaceColor)
                    .border(2.dp, primaryColor.copy(alpha = 0.65f), CircleShape),
            )
        }
    }
}

@Composable
fun EqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "b1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(460, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "b2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(320, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "b3",
    )

    val h1 = if (isPlaying) bar1 else 0.45f
    val h2 = if (isPlaying) bar2 else 0.75f
    val h3 = if (isPlaying) bar3 else 0.35f

    Row(
        modifier = modifier
            .width(20.dp)
            .height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(h1, h2, h3).forEach { fraction ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(18.dp * fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor),
            )
        }
    }
}
