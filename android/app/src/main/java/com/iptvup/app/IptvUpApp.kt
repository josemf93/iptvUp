package com.iptvup.app

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.iptvup.app.core.diag.DiagLog
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IptvUpApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        DiagLog.log("App iniciada v${BuildConfig.VERSION_NAME}")
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            FirebaseAuth.getInstance().useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 9099)
            FirebaseFirestore.getInstance().useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 8080)
        }
    }
}
