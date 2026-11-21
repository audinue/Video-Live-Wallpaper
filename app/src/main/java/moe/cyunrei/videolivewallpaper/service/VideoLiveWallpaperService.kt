package moe.cyunrei.videolivewallpaper.service

import android.app.WallpaperManager
import android.content.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File

class VideoLiveWallpaperService : WallpaperService() {
    internal inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var broadcastReceiver: BroadcastReceiver? = null
        private var playingVideoKey: String? = null
        private var playerFailed = false

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
            mediaPlayer?.release()
            mediaPlayer = null
            playingVideoKey = null
        }

        private fun startPlayer() {
            releasePlayer()
            playerFailed = false
            val source = readVideoSource() ?: return
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
                        playerFailed = true
                        true
                    }
                    prepare()
                    val volume = if (File("$filesDir/unmute").exists()) 1.0f else 0f
                    setVolume(volume, volume)
                    start()
                }
            }.getOrElse {
                it.printStackTrace()
                playerFailed = true
                null
            }
            playingVideoKey = videoKey(source)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startPlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (!visible) {
                mediaPlayer?.pause()
                return
            }
            val source = readVideoSource()
            val needsNewPlayer = mediaPlayer == null || playerFailed ||
                (source != null && videoKey(source) != playingVideoKey)
            if (needsNewPlayer) startPlayer() else mediaPlayer?.start()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            releasePlayer()
        }

        override fun onDestroy() {
            super.onDestroy()
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