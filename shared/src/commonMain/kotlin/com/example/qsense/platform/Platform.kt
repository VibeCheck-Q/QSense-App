package com.example.qsense.platform

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
