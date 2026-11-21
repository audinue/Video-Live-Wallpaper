package moe.cyunrei.videolivewallpaper.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import moe.cyunrei.videolivewallpaper.R
import moe.cyunrei.videolivewallpaper.service.VideoLiveWallpaperService

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        findViewById<Button>(R.id.choose_video_file).setOnClickListener { chooseVideo() }
        findViewById<Button>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun chooseVideo() {
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }.also { startActivityForResult(it, CHOOSE_VIDEO_REQUEST_CODE) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CHOOSE_VIDEO_REQUEST_CODE || resultCode != RESULT_OK) return
        val videoUri: Uri = data?.data ?: return
        for (previousPermission in contentResolver.persistedUriPermissions) {
            if (previousPermission.uri != videoUri) {
                contentResolver.releasePersistableUriPermission(
                    previousPermission.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        contentResolver.takePersistableUriPermission(videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        openFileOutput("video_live_wallpaper_file_path", Context.MODE_PRIVATE).use {
            it.write(videoUri.toString().toByteArray())
        }
        VideoLiveWallpaperService.setToWallPaper(this)
    }

    companion object {
        private const val CHOOSE_VIDEO_REQUEST_CODE = 1
    }
}
