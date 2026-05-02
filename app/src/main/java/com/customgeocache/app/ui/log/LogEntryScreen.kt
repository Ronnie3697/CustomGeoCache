package com.customgeocache.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.customgeocache.app.data.api.GcLogApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogEntryScreen(
    gccode: String,
    onBack: () -> Unit,
    onSubmitted: () -> Unit
) {
    val vm: LogEntryViewModel = viewModel(factory = LogEntryViewModel.factory(gccode))
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.submittedLogCode != null) {
        SubmittedScreen(
            logCode = state.submittedLogCode!!,
            onContinue = onSubmitted
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Logovat keš", style = MaterialTheme.typography.titleMedium)
                        Text("$gccode — ${state.cacheName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Typ logu", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GcLogApi.LogType.entries.toList()) { logType ->
                    FilterChip(
                        selected = state.type == logType,
                        onClick = { vm.setType(logType) },
                        label = { Text(logType.display) }
                    )
                }
            }

            Spacer(Modifier.size(20.dp))

            OutlinedTextField(
                value = state.text,
                onValueChange = vm::setText,
                label = { Text("Text logu") },
                placeholder = { Text("Tady piš svůj log...") },
                minLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )

            Spacer(Modifier.size(16.dp))

            if (state.type == GcLogApi.LogType.FOUND) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Switch(
                        checked = state.usedFavoritePoint,
                        onCheckedChange = vm::setUsedFavoritePoint
                    )
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("Použít favorite point", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Pouze pro premium členy.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.error != null) {
                Spacer(Modifier.size(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.error!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.size(24.dp))

            Button(
                onClick = vm::submit,
                enabled = !state.submitting && state.text.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Odeslat log")
                }
            }
        }
    }
}

@Composable
private fun SubmittedScreen(logCode: String, onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp)
        )
        Spacer(Modifier.size(16.dp))
        Text(
            "Log odeslán!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "Reference: $logCode",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onContinue) { Text("Hotovo") }
    }
}
