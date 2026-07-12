package com.example.qsense.presentation.feature.vision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qsense.di.AppContainer
import com.example.qsense.presentation.theme.QSenseColors

/**
 * v2 vision: capture a part photo, send it to the PC service over MQTT, and show the annotated
 * result + diagnosis. [machineNo]/[partNo] tag the request for the PC-side diagnosis.
 */
@Composable
fun VisionScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    machineNo: String = "manual",
    partNo: String = "blade",
    viewModel: VisionViewModel = viewModel { VisionViewModel(container) },
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(
                "Scan part",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = QSenseColors.ink,
            )
        }

        when (val s = state) {
            VisionUiState.Idle -> CameraCapture(
                onCaptured = { viewModel.submitImage(machineNo, partNo, it) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            VisionUiState.Sending, VisionUiState.Waiting -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.padding(6.dp))
                    Text(
                        if (s is VisionUiState.Sending) "Sending image…" else "Waiting for PC…",
                        color = QSenseColors.inkSoft,
                    )
                }
            }

            is VisionUiState.Result -> {
                AnnotatedImage(
                    imageB64 = s.response.annotatedImageB64,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Card(shape = RoundedCornerShape(18.dp)) {
                    Text(
                        s.response.diagnosis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = QSenseColors.ink,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Button(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan again")
                }
            }

            is VisionUiState.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = QSenseColors.ink)
                    Spacer(Modifier.padding(6.dp))
                    Button(onClick = viewModel::reset) { Text("Retry") }
                }
            }
        }
    }
}
