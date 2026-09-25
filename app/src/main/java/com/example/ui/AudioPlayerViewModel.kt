package com.example.ui

import android.app.Application
import android.content.ComponentName
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.AppDatabase
import com.example.data.StreamEntity
import com.example.service.AudioPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentUrl: String = "",
    val currentTitle: String = "No Stream Loaded",
    val currentSubtitle: String = "Ready to stream",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLiveStream: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val isMuted: Boolean = false,
    val errorMessage: String? = null,
    val isConnected: Boolean = false
)

class AudioPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val db = AppDatabase.getInstance(application)
    private val streamDao = db.streamDao()

    val savedStreams: StateFlow<List<StreamEntity>> = streamDao.getAllStreams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var mediaController: MediaController? = null
    private var progressUpdateJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressPolling()
            } else {
                stopProgressPolling()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isBuffering = true) }
                }
                Player.STATE_READY -> {
                    val duration = mediaController?.duration ?: 0L
                    val isLive = duration <= 0L || duration == androidx.media3.common.C.TIME_UNSET
                    _uiState.update {
                        it.copy(
                            isBuffering = false,
                            durationMs = if (isLive) 0L else duration,
                            isLiveStream = isLive,
                            errorMessage = null
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
                    stopProgressPolling()
                }
                Player.STATE_IDLE -> {
                    _uiState.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val title = mediaItem?.mediaMetadata?.title?.toString() ?: "Live Audio Stream"
            val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Direct Stream"
            val uri = mediaItem?.requestMetadata?.mediaUri?.toString() ?: ""
            _uiState.update {
                it.copy(
                    currentTitle = title,
                    currentSubtitle = artist,
                    currentUrl = if (uri.isNotEmpty()) uri else it.currentUrl
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val msg = error.localizedMessage ?: "Playback error encountered. Please check the stream URL."
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = msg
                )
            }
            stopProgressPolling()
        }
    }

    init {
        connectToService()
    }

    private fun connectToService() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener(
            {
                try {
                    val controller = controllerFuture.get()
                    mediaController = controller
                    controller.addListener(playerListener)

                    val isPlaying = controller.isPlaying
                    val isBuffering = controller.playbackState == Player.STATE_BUFFERING
                    val duration = controller.duration
                    val isLive = duration <= 0L || duration == androidx.media3.common.C.TIME_UNSET

                    val currentItem = controller.currentMediaItem
                    val title = currentItem?.mediaMetadata?.title?.toString() ?: "Audio Stream"
                    val artist = currentItem?.mediaMetadata?.artist?.toString() ?: "Continuous Playback"
                    val uri = currentItem?.requestMetadata?.mediaUri?.toString() ?: ""

                    _uiState.update {
                        it.copy(
                            isConnected = true,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                            durationMs = if (isLive) 0L else duration,
                            isLiveStream = isLive,
                            currentTitle = title,
                            currentSubtitle = artist,
                            currentUrl = uri
                        )
                    }

                    if (isPlaying) {
                        startProgressPolling()
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            errorMessage = "Could not connect to AudioPlaybackService: ${e.message}"
                        )
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun playStream(url: String, title: String? = null, subtitle: String? = null) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid audio stream URL") }
            return
        }

        val displayTitle = if (!title.isNullOrBlank()) title else deriveTitleFromUrl(cleanUrl)
        val displaySubtitle = subtitle ?: "Direct Stream"

        _uiState.update {
            it.copy(
                currentUrl = cleanUrl,
                currentTitle = displayTitle,
                currentSubtitle = displaySubtitle,
                isBuffering = true,
                errorMessage = null
            )
        }

        val controller = mediaController
        if (controller != null) {
            val metadata = MediaMetadata.Builder()
                .setTitle(displayTitle)
                .setArtist(displaySubtitle)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(cleanUrl)
                .setMediaMetadata(metadata)
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()

            // Save to database history
            viewModelScope.launch {
                val existing = streamDao.getStreamByUrl(cleanUrl)
                if (existing != null) {
                    streamDao.updateLastPlayed(cleanUrl, System.currentTimeMillis())
                } else {
                    streamDao.insertStream(
                        StreamEntity(
                            title = displayTitle,
                            url = cleanUrl,
                            subtitle = displaySubtitle,
                            isFavorite = false,
                            lastPlayedTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        } else {
            _uiState.update { it.copy(errorMessage = "Connecting to audio service... please try again.") }
            connectToService()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && _uiState.value.currentUrl.isNotEmpty()) {
                playStream(_uiState.value.currentUrl, _uiState.value.currentTitle, _uiState.value.currentSubtitle)
            } else {
                controller.play()
            }
        }
    }

    fun stop() {
        val controller = mediaController ?: return
        controller.stop()
        stopProgressPolling()
        _uiState.update {
            it.copy(
                isPlaying = false,
                isBuffering = false,
                currentPositionMs = 0L
            )
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekForward10s() {
        val controller = mediaController ?: return
        val newPos = (controller.currentPosition + 10_000L).coerceAtMost(controller.duration.coerceAtLeast(0L))
        controller.seekTo(newPos)
        _uiState.update { it.copy(currentPositionMs = newPos) }
    }

    fun seekBack10s() {
        val controller = mediaController ?: return
        val newPos = (controller.currentPosition - 10_000L).coerceAtLeast(0L)
        controller.seekTo(newPos)
        _uiState.update { it.copy(currentPositionMs = newPos) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val controller = mediaController ?: return
        controller.playbackParameters = PlaybackParameters(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleMute() {
        val controller = mediaController ?: return
        val willMute = !_uiState.value.isMuted
        controller.volume = if (willMute) 0.0f else 1.0f
        _uiState.update { it.copy(isMuted = willMute) }
    }

    fun toggleFavorite(stream: StreamEntity) {
        viewModelScope.launch {
            streamDao.updateFavoriteStatus(stream.id, !stream.isFavorite)
        }
    }

    fun deleteStream(stream: StreamEntity) {
        viewModelScope.launch {
            streamDao.deleteStream(stream)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun startProgressPolling() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration
                    val isLive = dur <= 0L || dur == androidx.media3.common.C.TIME_UNSET
                    _uiState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = if (isLive) 0L else dur,
                            isLiveStream = isLive
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressPolling() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private fun deriveTitleFromUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val lastPath = uri.lastPathSegment
            if (!lastPath.isNullOrBlank()) {
                lastPath.substringBeforeLast(".")
                    .replace("-", " ")
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
            } else {
                uri.host ?: "Audio Stream"
            }
        } catch (_: Exception) {
            "Custom Audio Stream"
        }
    }

    override fun onCleared() {
        stopProgressPolling()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        mediaController = null
        super.onCleared()
    }
}
