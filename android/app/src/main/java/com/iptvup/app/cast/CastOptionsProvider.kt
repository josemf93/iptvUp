package com.iptvup.app.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(DEFAULT_RECEIVER_APPLICATION_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null

    private companion object {
        // Receptor multimedia por defecto de Google (HLS/DASH/MP4).
        const val DEFAULT_RECEIVER_APPLICATION_ID = "CC1AD845"
    }
}
