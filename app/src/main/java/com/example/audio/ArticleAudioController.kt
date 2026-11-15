package com.example.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class AudioEngineState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val durationSeconds: Int = 120,
    val speed: Float = 1.0f,
)

class ArticleAudioController(
    context: Context,
    private val scope: CoroutineScope,
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var tickerJob: Job? = null
    private var currentText: String = ""

    private val _engineState = MutableStateFlow(AudioEngineState())
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    init {
        try {
            tts = TextToSpeech(appContext, this)
        } catch (_: Exception) {
            // Gracefully fall back to visual timer playback when TTS service is unavailable
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    scope.launch {
                        if (_engineState.value.progress >= 0.96f) {
                            stopInternal()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {}
            })
            _engineState.update { it.copy(isReady = true) }
        }
    }

    fun play(text: String, durationSeconds: Int, speed: Float, startProgress: Float = 0f) {
        currentText = text
        val clampedDuration = durationSeconds.coerceAtLeast(15)
        val clampedProgress = startProgress.coerceIn(0f, 0.99f)

        _engineState.update {
            it.copy(
                isPlaying = true,
                progress = clampedProgress,
                durationSeconds = clampedDuration,
                speed = speed,
            )
        }

        speakFromProgress(clampedProgress, speed)
        startTicker()
    }

    fun togglePlayPause() {
        val state = _engineState.value
        if (state.isPlaying) {
            pause()
        } else if (currentText.isNotBlank()) {
            play(
                text = currentText,
                durationSeconds = state.durationSeconds,
                speed = state.speed,
                startProgress = if (state.progress >= 0.99f) 0f else state.progress,
            )
        }
    }

    fun pause() {
        tickerJob?.cancel()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _engineState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        val wasPlaying = _engineState.value.isPlaying
        _engineState.update { it.copy(progress = clamped) }
        if (wasPlaying && currentText.isNotBlank()) {
            speakFromProgress(clamped, _engineState.value.speed)
        }
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.75f, 2.0f)
        val wasPlaying = _engineState.value.isPlaying
        _engineState.update { it.copy(speed = clamped) }
        if (wasPlaying && currentText.isNotBlank()) {
            speakFromProgress(_engineState.value.progress, clamped)
        }
    }

    fun stop() {
        stopInternal()
        currentText = ""
    }

    private fun stopInternal() {
        tickerJob?.cancel()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _engineState.update { it.copy(isPlaying = false, progress = 0f) }
    }

    private fun speakFromProgress(progress: Float, speed: Float) {
        val engine = tts ?: return
        if (!_engineState.value.isReady || currentText.isBlank()) return
        try {
            engine.setSpeechRate(speed)
            val startIndex = (currentText.length * progress).toInt()
                .coerceIn(0, (currentText.length - 1).coerceAtLeast(0))
            val remaining = currentText.substring(startIndex).trim().ifEmpty { currentText }
            val params = Bundle()
            engine.speak(remaining, TextToSpeech.QUEUE_FLUSH, params, "onenews_brief_${System.currentTimeMillis()}")
        } catch (_: Exception) {
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && _engineState.value.isPlaying) {
                delay(250L)
                val state = _engineState.value
                if (!state.isPlaying) break
                val step = (0.25f * state.speed) / state.durationSeconds.toFloat()
                val nextProgress = (state.progress + step).coerceAtMost(1.0f)
                if (nextProgress >= 1.0f) {
                    stopInternal()
                    break
                } else {
                    _engineState.update { it.copy(progress = nextProgress) }
                }
            }
        }
    }

    fun shutdown() {
        tickerJob?.cancel()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }
}
