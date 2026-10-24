package com.luissalet.hoardgrab

import android.app.Application

class GrabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifs.createChannels(this)
        // Unpacking Python takes a few seconds on first run; do it before the user needs it.
        Thread { runCatching { Engine.ensureInit(this) } }.start()
    }
}
