package com.example.qsense.presentation.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qsense.di.AppContainer
import com.example.qsense.domain.model.AlertItem
import com.example.qsense.domain.service.ConnectionState
import com.example.qsense.domain.service.ModelStatus
import com.example.qsense.presentation.brand.QSenseLogo
import com.example.qsense.presentation.theme.QSenseColors
import com.example.qsense.presentation.theme.classifySeverity
import com.example.qsense.presentation.theme.humidityColor
import com.example.qsense.presentation.theme.temperatureColor

@Composable
fun DashboardScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel { DashboardViewModel(container) },
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header()
        StatusRow(uiState.modelStatus, uiState.connectionState)
        HorizontalDivider(color = QSenseColors.border)

        if (uiState.alerts.isEmpty()) {
            Text(
                "Waiting for fault alerts…",
                style = MaterialTheme.typography.bodyMedium,
                color = QSenseColors.inkSoft,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.alerts, key = { it.alert.alertId }) { item ->
                    AlertRow(
                        item = item,
                        selected = item.alert.alertId == uiState.selectedAlertId,
                        onClick = { viewModel.onSelectAlert(item.alert.alertId) },
                    )
                }
            }
        }

        if (uiState.selectedAlertId != null) {
            HorizontalDivider(color = QSenseColors.border)
            DiagnosisPanel(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QSenseLogo(size = 40.dp)
        Column {
            Text(
                "QSense",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = QSenseColors.ink,
            )
            Text(
                "Predictive maintenance",
                style = MaterialTheme.typography.labelMedium,
                color = QSenseColors.inkSoft,
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

/** A status dot always paired with a text label (never color-only), per the design guardrail. */
@Composable
private fun StatusItem(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = QSenseColors.ink)
    }
}

@Composable
private fun StatusRow(modelStatus: ModelStatus, connectionState: ConnectionState) {
    val (modelColor, modelLabel) = when (modelStatus) {
        ModelStatus.Loading -> QSenseColors.amber to "Model: loading…"
        ModelStatus.Ready -> QSenseColors.sage to "Model: ready"
        is ModelStatus.Error -> QSenseColors.coral to "Model: ${modelStatus.message}"
    }
    val (connColor, connLabel) = when (connectionState) {
        ConnectionState.Connecting -> QSenseColors.amber to "MQTT: connecting…"
        ConnectionState.Connected -> QSenseColors.sage to "MQTT: connected"
        ConnectionState.Disconnected -> QSenseColors.off to "MQTT: disconnected"
        is ConnectionState.Error -> QSenseColors.coral to "MQTT: ${connectionState.message}"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StatusItem(modelColor, modelLabel)
        StatusItem(connColor, connLabel)
    }
}

/** Drops a trailing ".0" so 78.0f reads as "78" but 78.5f stays "78.5". */
private fun trimNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

/** A small pill badge showing a criticality (or RESOLVED) label in its color. */
@Composable
private fun Pill(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A small pill: colored dot + "Label value" — used for temperature/humidity readouts. */
@Composable
private fun SensorChip(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .background(QSenseColors.bgSoft, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color)
        Text(
            "$label $value",
            style = MaterialTheme.typography.labelMedium,
            color = QSenseColors.ink,
        )
    }
}

@Composable
private fun AlertRow(item: AlertItem, selected: Boolean, onClick: () -> Unit) {
    val criticality = classifySeverity(item.alert.severity)
    val accent = if (item.resolved) QSenseColors.sage else criticality.color
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = QSenseColors.bg),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) QSenseColors.tealCta else QSenseColors.border,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left accent bar in the stage color.
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(
                modifier = Modifier.padding(14.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${item.alert.machineNo} • ${item.alert.partName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = QSenseColors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.resolved) {
                        Pill("RESOLVED", QSenseColors.sage)
                    } else {
                        Pill(criticality.label, criticality.color)
                    }
                }
                Text(
                    "Part ${item.alert.partNo} • severity ${item.alert.severity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = QSenseColors.inkSoft,
                )
                val temp = item.alert.temperature
                val humidity = item.alert.humidity
                if (temp != null || humidity != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (temp != null) {
                            SensorChip("Temp", "${trimNumber(temp)}°C", temperatureColor(temp))
                        }
                        if (humidity != null) {
                            SensorChip("Humidity", "${trimNumber(humidity)}%", humidityColor(humidity))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosisPanel(
    uiState: DashboardUiState,
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Likely causes & fixes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = QSenseColors.ink,
        )

        when (val diagnosis = uiState.diagnosis) {
            DiagnosisState.Idle -> Unit
            DiagnosisState.Generating -> CircularProgressIndicator(color = QSenseColors.tealCta)
            is DiagnosisState.Error ->
                Text("Diagnosis failed: ${diagnosis.message}", color = MaterialTheme.colorScheme.error)
            is DiagnosisState.Ready -> {
                if (diagnosis.diagnosis.causes.isEmpty()) {
                    Text(
                        "No causes could be parsed from the model output.",
                        color = QSenseColors.inkSoft,
                    )
                } else {
                    diagnosis.diagnosis.causes.forEachIndexed { index, cause ->
                        CauseCard(
                            number = index + 1,
                            cause = cause.cause,
                            fix = cause.fix,
                            selected = uiState.selectedCauseIndex == index,
                            onClick = { viewModel.onSelectCause(index) },
                        )
                    }

                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = viewModel::onNotesChange,
                        label = { Text("Operator notes") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = viewModel::onResolve,
                        enabled = uiState.canResolve,
                        modifier = Modifier.semantics {
                            contentDescription = "Resolve alert and publish acknowledgement"
                        },
                    ) {
                        Text("Resolve")
                    }

                    when (val resolve = uiState.resolve) {
                        ResolveState.Idle -> Unit
                        ResolveState.Publishing ->
                            Text("Publishing…", color = QSenseColors.inkSoft)
                        ResolveState.Done ->
                            Text("Resolved and published.", color = QSenseColors.sage)
                        is ResolveState.Error ->
                            Text("Publish failed: ${resolve.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CauseCard(
    number: Int,
    cause: String,
    fix: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = QSenseColors.bg),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) QSenseColors.tealCta else QSenseColors.border,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Text(
                    "$number. $cause",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = QSenseColors.ink,
                )
                Text(
                    "Fix: $fix",
                    style = MaterialTheme.typography.bodySmall,
                    color = QSenseColors.inkSoft,
                )
            }
        }
    }
}
