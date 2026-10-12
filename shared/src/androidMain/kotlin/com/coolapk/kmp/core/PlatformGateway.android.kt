package com.coolapk.kmp.core

actual fun createPlatformGateway(): CoolapkGateway = RustCoolapkGateway()
