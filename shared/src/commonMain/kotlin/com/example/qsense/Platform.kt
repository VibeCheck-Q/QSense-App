package com.example.qsense

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform