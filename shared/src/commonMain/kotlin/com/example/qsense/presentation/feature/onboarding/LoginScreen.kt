package com.example.qsense.presentation.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qsense.presentation.brand.QSenseLogo
import com.example.qsense.presentation.theme.QSenseColors

/**
 * Placeholder sign-in gate (no real auth in v1): any credentials advance to the dashboard via
 * [onSignIn]. Styled with the QSense design tokens.
 */
@Composable
fun LoginScreen(onSignIn: (String, String) -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = QSenseColors.tealCta,
        unfocusedBorderColor = QSenseColors.border,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = QSenseColors.bgSoft,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 26.dp)
            .padding(top = 64.dp, bottom = 24.dp),
    ) {
        // brand row
        Row(verticalAlignment = Alignment.CenterVertically) {
            QSenseLogo(size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "QSense",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = QSenseColors.ink,
            )
        }

        Spacer(Modifier.height(38.dp))
        Text(
            "Welcome back",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = QSenseColors.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in to monitor the floor.",
            style = MaterialTheme.typography.bodyMedium,
            color = QSenseColors.inkSoft,
        )

        Spacer(Modifier.height(30.dp))
        Text("Username", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = QSenseColors.inkSoft)
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            singleLine = true,
            placeholder = { Text("floor.tech", color = QSenseColors.inkSoft.copy(alpha = 0.6f)) },
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))
        Text("Password", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = QSenseColors.inkSoft)
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            singleLine = true,
            placeholder = { Text("••••••••", color = QSenseColors.inkSoft.copy(alpha = 0.6f)) },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { show = !show }) {
                    Text(if (show) "Hide" else "Show", color = QSenseColors.tealCta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { /* placeholder: no forgot-password flow in v1 */ }) {
                Text("Forgot password?", color = QSenseColors.tealCta, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                signedIn = true
                onSignIn(user, pass)
            },
            colors = ButtonDefaults.buttonColors(containerColor = QSenseColors.tealCta),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                if (signedIn) "Signed in ✓" else "Sign in",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }

        AnimatedVisibility(visible = signedIn) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(QSenseColors.sageSoft)
                    .padding(11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(QSenseColors.sage))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Signed in — loading floor…",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2F6B49),
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("New device? ", style = MaterialTheme.typography.bodySmall, color = QSenseColors.inkSoft)
            TextButton(onClick = { /* placeholder: no request-access flow in v1 */ }) {
                Text("Request access", color = QSenseColors.tealCta, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
