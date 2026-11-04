package com.craftcam.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.craftcam.app.R
import kotlin.random.Random

object SoundManager {
    private const val TAG = "SoundManager"
    private var soundPool: SoundPool? = null
    private val clickSounds = mutableListOf<Int>()
    private var advanceSoundId: Int = 0
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build().also { sp ->
                    clickSounds.clear()
                    try {
                        clickSounds.add(sp.load(context, R.raw.sound_slidep_click, 1))
                        clickSounds.add(sp.load(context, R.raw.sound_slidep_click2, 1))
                        clickSounds.add(sp.load(context, R.raw.sound_slidep_click3, 1))
                        advanceSoundId = sp.load(context, R.raw.sound_slidep_end, 1)
                        isInitialized = true
                        Log.d(TAG, "Dazz shutter sound effects loaded successfully")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not load shutter sound resources", e)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "SoundPool initialization failed", e)
        }
    }

    fun playShutterSound() {
        // Silent photo capture: sound disabled
    }

    fun playFilmAdvanceSound() {
        // Silent photo capture: sound disabled
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        clickSounds.clear()
        isInitialized = false
    }
}

