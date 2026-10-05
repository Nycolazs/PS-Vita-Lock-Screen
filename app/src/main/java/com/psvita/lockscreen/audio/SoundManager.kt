package com.psvita.lockscreen.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.psvita.lockscreen.R
import com.psvita.lockscreen.data.LockPreferences

class SoundManager(context: Context) {

    private val prefs = LockPreferences(context)
    private val soundPool: SoundPool
    private var peelSoundId: Int = 0
    private var unlockSoundId: Int = 0
    private var isPeelLoaded: Boolean = false
    private var isUnlockLoaded: Boolean = false

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                if (sampleId == peelSoundId) isPeelLoaded = true
                if (sampleId == unlockSoundId) isUnlockLoaded = true
            }
        }

        peelSoundId = soundPool.load(context, R.raw.vita_peel, 1)
        unlockSoundId = soundPool.load(context, R.raw.vita_unlock, 1)
    }

    fun playPeelSound(volume: Float = 0.85f) {
        if (!prefs.isSoundEnabled || !isPeelLoaded) return
        soundPool.play(peelSoundId, volume, volume, 1, 0, 1.0f)
    }

    fun playUnlockSound(volume: Float = 1.0f) {
        if (!prefs.isSoundEnabled || !isUnlockLoaded) return
        soundPool.play(unlockSoundId, volume, volume, 1, 0, 1.0f)
    }

    fun release() {
        soundPool.release()
    }
}
