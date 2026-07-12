package com.example.qsense.presentation.feature.vision

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    // Operator-editable labels sent with the photo. Pre-filled; type the real machine/part from the
    // monitoring alert. The model detects the part from the image — these are tags for the request.
    var machine by remember { mutableStateOf(machineNo) }
    var part by remember { mutableStateOf(partNo) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Scan part",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = QSenseColors.ink,
                )
                Text(
                    partNo,
                    style = MaterialTheme.typography.labelMedium,
                    color = QSenseColors.inkSoft,
                )
            }
        }

        val frame = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(QSenseColors.border.copy(alpha = 0.25f))
            .border(1.dp, QSenseColors.border, RoundedCornerShape(18.dp))

        when (val s = state) {
            VisionUiState.Idle -> Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = machine,
                        onValueChange = { machine = it },
                        label = { Text("Machine") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = part,
                        onValueChange = { part = it },
                        label = { Text("Part name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                CameraCapture(
                    onCaptured = { viewModel.submitImage(machine, part, it) },
                    modifier = frame,
                )
                Text(
                    "Fill in the machine & part, aim at it, and tap Capture.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = QSenseColors.inkSoft,
                )
            }

            VisionUiState.Sending, VisionUiState.Waiting -> Box(frame, Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (s is VisionUiState.Sending) "Sending image…" else "Analysing on PC…",
                        color = QSenseColors.inkSoft,
                    )
                }
            }

            is VisionUiState.Result -> {
                AnnotatedImage(imageB64 = s.response.annotatedImageB64, modifier = frame)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = QSenseColors.bgSoft),
                    modifier = Modifier.fillMaxWidth(),
                ) {
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

            is VisionUiState.Error -> Box(frame, Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(s.message, color = QSenseColors.ink)
                    Button(onClick = viewModel::reset) { Text("Retry") }
                }
            }
        }
    }
}
