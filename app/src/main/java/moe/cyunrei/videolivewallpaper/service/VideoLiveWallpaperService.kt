package moe.cyunrei.videolivewallpaper.service

import android.app.WallpaperManager
import android.content.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File

class VideoLiveWallpaperService : WallpaperService() {
    internal inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var broadcastReceiver: BroadcastReceiver? = null
        private var playingVideoKey: String? = null
        private var resumePositionMilliseconds = 0
        private var resumeVideoKey: String? = null
        private val mainHandler = Handler(Looper.getMainLooper())

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val intentFilter = IntentFilter(VIDEO_PARAMS_CONTROL_ACTION)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.getBooleanExtra(KEY_ACTION, false)
                    if (action) {
                        mediaPlayer?.setVolume(0f, 0f)
                    } else {
                        mediaPlayer?.setVolume(1.0f, 1.0f)
                    }
                }
            }
            broadcastReceiver = receiver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, intentFilter)
            }
        }

        private fun readVideoSource(): String? = runCatching {
            this@VideoLiveWallpaperService.openFileInput("video_live_wallpaper_file_path")
                .bufferedReader().use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() }

        private fun videoKey(source: String): String =
            if (source.startsWith("content://")) source else "$source@${File(source).lastModified()}"

        private fun releasePlayer() {
            val player = mediaPlayer ?: return
            resumePositionMilliseconds = runCatching { player.currentPosition }.getOrDefault(0)
            resumeVideoKey = playingVideoKey
            player.release()
            mediaPlayer = null
            playingVideoKey = null
        }

        private fun restartPlayerAfterFailure() {
            mainHandler.post {
                releasePlayer()
                if (isVisible) startPlayer()
            }
        }

        private fun startPlayer() {
            releasePlayer()
            val source = readVideoSource() ?: return
            val key = videoKey(source)
            val surface = surfaceHolder.surface ?: return
            if (!surface.isValid) return
            mediaPlayer = runCatching {
                MediaPlayer().apply {
                    setSurface(surface)
                    if (source.startsWith("content://")) {
                        setDataSource(this@VideoLiveWallpaperService, Uri.parse(source))
                    } else {
                        setDataSource(source)
                    }
                    isLooping = true
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    setOnErrorListener { _, _, _ ->
                        restartPlayerAfterFailure()
                        true
                    }
                    setOnInfoListener { _, what, _ ->
                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_NOT_PLAYING) restartPlayerAfterFailure()
                        false
                    }
                    prepare()
                    if (key == resumeVideoKey && resumePositionMilliseconds in 1 until duration) {
                        seekTo(resumePositionMilliseconds)
                    }
                    val volume = if (File("$filesDir/unmute").exists()) 1.0f else 0f
                    setVolume(volume, volume)
                    start()
                }
            }.getOrElse {
                it.printStackTrace()
                null
            }
            playingVideoKey = if (mediaPlayer != null) key else null
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            if (isVisible) startPlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) startPlayer() else releasePlayer()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            releasePlayer()
        }

        override fun onDestroy() {
            super.onDestroy()
            mainHandler.removeCallbacksAndMessages(null)
            releasePlayer()
            unregisterReceiver(broadcastReceiver)
        }
    }

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    companion object {
        const val VIDEO_PARAMS_CONTROL_ACTION = "moe.cyunrei.livewallpaper"
        private const val KEY_ACTION = "music"
        private const val ACTION_MUSIC_UNMUTE = false
        private const val ACTION_MUSIC_MUTE = true
        fun muteMusic(context: Context) {
            Intent(VIDEO_PARAMS_CONTROL_ACTION).apply {
                setPackage(context.packageName)
                putExtra(KEY_ACTION, ACTION_MUSIC_MUTE)
            }.also { context.sendBroadcast(it) }
        }

        fun unmuteMusic(context: Context) {
            Intent(VIDEO_PARAMS_CONTROL_ACTION).apply {
                setPackage(context.packageName)
                putExtra(KEY_ACTION, ACTION_MUSIC_UNMUTE)
            }.also {
                context.sendBroadcast(it)
            }
        }

        fun setToWallPaper(context: Context) {
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, VideoLiveWallpaperService::class.java)
                )
            }.also {
                context.startActivity(it)
            }
        }
    }
}