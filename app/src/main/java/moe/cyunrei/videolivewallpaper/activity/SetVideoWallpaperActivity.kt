package moe.cyunrei.videolivewallpaper.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import moe.cyunrei.videolivewallpaper.R
import moe.cyunrei.videolivewallpaper.service.VideoLiveWallpaperService
import java.io.File

class SetVideoWallpaperActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (videoUri == null) {
            finish()
            return
        }
        Toast.makeText(this, R.string.preparing_video, Toast.LENGTH_SHORT).show()
        Thread {
            val storedVideoPath = runCatching { copyVideoToPrivateStorage(videoUri) }.getOrNull()
            runOnUiThread {
                if (storedVideoPath == null) {
                    Toast.makeText(this, R.string.video_could_not_be_read, Toast.LENGTH_LONG).show()
                } else {
                    openFileOutput("video_live_wallpaper_file_path", Context.MODE_PRIVATE).use {
                        it.write(storedVideoPath.toByteArray())
                    }
                    VideoLiveWallpaperService.setToWallPaper(this)
                }
                finish()
            }
        }.start()
    }

    private fun copyVideoToPrivateStorage(videoUri: Uri): String {
        val temporaryFile = File(filesDir, "opened_video.tmp")
        val videoFile = File(filesDir, "opened_video")
        contentResolver.openInputStream(videoUri)!!.use { input ->
            temporaryFile.outputStream().use { output -> input.copyTo(output) }
        }
        if (!temporaryFile.renameTo(videoFile)) {
            videoFile.delete()
            temporaryFile.renameTo(videoFile)
        }
        return videoFile.absolutePath
    }
}
