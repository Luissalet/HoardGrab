package com.luissalet.hoardgrab

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Apps targeting Android 15+ draw edge to edge: keep the layout's own padding and add the bars (and keyboard) on top. */
fun View.padForSystemBars() {
    val l = paddingLeft
    val t = paddingTop
    val r = paddingRight
    val b = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val s = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
        v.setPadding(l + s.left, t + s.top, r + s.right, b + s.bottom)
        WindowInsetsCompat.CONSUMED
    }
}
