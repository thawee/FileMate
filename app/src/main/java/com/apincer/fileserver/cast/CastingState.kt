package com.apincer.fileserver.cast

import kotlinx.coroutines.flow.MutableStateFlow

object CastingState {
    val activeCaster = MutableStateFlow<TvCaster?>(null)
    
    // UI states
    val isTransferring = MutableStateFlow(false)
    val showCastSheet = MutableStateFlow(false)
    
    // Background Slideshow State
    val slideshowActive = MutableStateFlow(false)
    val slides = MutableStateFlow<List<com.apincer.fileserver.ui.SlideItem>>(emptyList())
    val currentIndex = MutableStateFlow(0)
    val isPlaying = MutableStateFlow(false)
    val timerSeconds = MutableStateFlow(5)
}
