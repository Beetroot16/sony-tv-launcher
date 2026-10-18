package dev.dhruv.tvhome

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Quiet UI clicks: one for focus moves, one for OK / launch. */
class Sounds(context: Context, var enabled: Boolean) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val focusId = pool.load(context, R.raw.focus, 1)
    private val selectId = pool.load(context, R.raw.select, 1)

    fun focus() {
        if (enabled) pool.play(focusId, 0.35f, 0.35f, 1, 0, 1f)
    }

    fun select() {
        if (enabled) pool.play(selectId, 0.5f, 0.5f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
