package com.coolapk.kmp.core

import android.content.Context

private var applicationContext: Context? = null

actual fun initializePlatformGateway(context: Any) {
    applicationContext = (context as? Context)?.applicationContext
}

actual fun createPlatformGateway(): CoolapkGateway = RustCoolapkGateway(applicationContext)
