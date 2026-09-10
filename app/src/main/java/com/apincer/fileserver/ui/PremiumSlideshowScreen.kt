package com.apincer.fileserver.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.google.android.gms.cast.framework.CastButtonFactory
import com.apincer.fileserver.cast.TvCaster
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.KeyboardArrowDown
data class SlideItem(
    val id: String,
    val imageUrl: String,
    val title: String,
    val description: String,
    val fetchImageBytes: (suspend () -> ByteArray?)? = null
)

@Composable

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun PremiumSlideshowScreen(
    discoveredDevices: List<TvCaster>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slides by com.apincer.fileserver.cast.CastingState.slides.collectAsState()
    val currentIndex by com.apincer.fileserver.cast.CastingState.currentIndex.collectAsState()
    val isPlaying by com.apincer.fileserver.cast.CastingState.isPlaying.collectAsState()
    val timerSeconds by com.apincer.fileserver.cast.CastingState.timerSeconds.collectAsState()
    val activeCaster by com.apincer.fileserver.cast.CastingState.activeCaster.collectAsState()

    val pagerState = rememberPagerState(initialPage = currentIndex, pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()
    
    // Casting State
    var showCastSheet by remember { mutableStateOf(false) }

    // Sync PagerState with Global State
    LaunchedEffect(currentIndex) {
        if (pagerState.currentPage != currentIndex && pagerState.targetPage != currentIndex) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }
    
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (com.apincer.fileserver.cast.CastingState.currentIndex.value != settled) {
                com.apincer.fileserver.cast.CastingState.currentIndex.value = settled
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. The Pager for Slides
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp
        ) { page ->
            val slide = slides[page]
            
            // Calculate parallax offset based on page offset
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                // Image with Parallax Effect
                AsyncImage(
                    model = java.io.File(slide.id),
                    contentDescription = slide.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Premium Parallax shifting
                            translationX = pageOffset * 200f
                            // Slight scale down when swiping away
                            scaleX = 1f - (pageOffset.absoluteValue * 0.1f)
                            scaleY = 1f - (pageOffset.absoluteValue * 0.1f)
                        }
                )

                // Dark Gradient overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                ),
                                startY = 500f
                            )
                        )
                )

                // Slide Info (Title & Description)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(32.dp)
                        .graphicsLayer {
                            // Animate text fading and shifting slightly
                            alpha = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
                            translationY = pageOffset.absoluteValue * 50f
                        }
                ) {
                    Text(
                        text = slide.title,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = slide.description,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // 2. Top Bar Controls (Close & Cast)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minimize Button
            IconButton(
                onClick = { onClose() },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize",
                    tint = Color.White
                )
            }

            // Cast Controls Area
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Timer Toggle
                val timerOptions = listOf(3, 5, 10, 30)
                TextButton(
                    onClick = { 
                        val nextIndex = (timerOptions.indexOf(timerSeconds) + 1) % timerOptions.size
                        com.apincer.fileserver.cast.CastingState.timerSeconds.value = timerOptions[nextIndex]
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Text("${timerSeconds}s", color = Color.White, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.width(12.dp))

                // Play / Pause Auto-Advance
                IconButton(
                    onClick = { com.apincer.fileserver.cast.CastingState.isPlaying.value = !isPlaying },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Slideshow",
                        tint = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .background(
                            if (activeCaster != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) 
                            else Color.Black.copy(alpha = 0.4f), 
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    UnifiedCastButton(iconTint = Color.White)
                }

            }
        }

        // 3. Page Indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(slides.size) { iteration ->
                val isSelected = pagerState.currentPage == iteration
                val color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f)
                val width = if (isSelected) 24.dp else 8.dp
                
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .height(8.dp)
                        .width(width)
                )
            }
        }
    }

}
