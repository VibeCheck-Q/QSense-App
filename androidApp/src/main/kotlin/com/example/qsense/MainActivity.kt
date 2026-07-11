package com.example.qsense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.qsense.presentation.App
import com.example.qsense.presentation.qsenseTypography

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as QSenseApplication).container
        setContent {
            App(container, typography = qsenseTypography())
        }
    }
}
