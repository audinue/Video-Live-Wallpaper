@file:Suppress("DEPRECATION")

package moe.cyunrei.videolivewallpaper.activity

import android.os.Build
import android.os.Bundle
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.PreferenceActivity
import android.preference.SwitchPreference
import moe.cyunrei.videolivewallpaper.R
import moe.cyunrei.videolivewallpaper.service.VideoLiveWallpaperService
import java.io.IOException

class SettingsActivity : PreferenceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.settings)
        (findPreference(this.resources.getString(R.string.preference_play_video_with_sound)) as SwitchPreference).apply {
            this.onPreferenceChangeListener =
                OnPreferenceChangeListener { _, _ ->
                    if (this.isChecked) {
                        this.isChecked = false
                        VideoLiveWallpaperService.muteMusic(this@SettingsActivity)
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Runtime.getRuntime().exec("rm " + filesDir.toPath() + "/unmute")
                            } else {
                                Runtime.getRuntime()
                                    .exec("rm /data/data/moe.cyunrei.videolivewallpaper/files/unmute")
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    } else {
                        this.isChecked = true
                        VideoLiveWallpaperService.unmuteMusic(this@SettingsActivity)
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Runtime.getRuntime().exec("touch " + filesDir.toPath() + "/unmute")
                            } else {
                                Runtime.getRuntime()
                                    .exec("touch /data/data/moe.cyunrei.videolivewallpaper/files/unmute")
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                    false
                }
        }
    }
}