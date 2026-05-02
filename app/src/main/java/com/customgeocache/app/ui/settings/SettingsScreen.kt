package com.customgeocache.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customgeocache.app.BuildConfig
import com.customgeocache.app.CustomGeoCacheApp
import com.customgeocache.app.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CustomGeoCacheApp
    val prefs = app.container.preferences
    val scope = rememberCoroutineScope()

    val gcUsername by prefs.gcUsername.collectAsStateWithLifecycle(initialValue = null)
    val mapyKey by prefs.mapyApiKey.collectAsStateWithLifecycle(initialValue = null)
    val folderUri by prefs.folderUri.collectAsStateWithLifecycle(initialValue = null)

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyDraft by remember { mutableStateOf("") }
    LaunchedEffect(mapyKey) { apiKeyDraft = mapyKey.orEmpty() }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* ignore */ }
            scope.launch { prefs.setFolderUri(uri.toString()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        ) {
            SectionTitle(stringResource(R.string.settings_section_account))
            ListItem(
                leadingContent = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                headlineContent = {
                    Text(gcUsername ?: stringResource(R.string.settings_login))
                },
                supportingContent = {
                    if (gcUsername != null) Text("geocaching.com")
                },
                trailingContent = {
                    if (gcUsername != null) {
                        IconButton(onClick = {
                            scope.launch { app.container.gcLogin.logout() }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.settings_logout))
                        }
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                    }
                }
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_section_map))
            ListItem(
                leadingContent = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_mapy_key)) },
                supportingContent = {
                    Text(if (mapyKey.isNullOrBlank()) "Nenastaveno" else maskKey(mapyKey!!))
                },
                modifier = Modifier.clickable { showApiKeyDialog = true }
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_section_storage))
            ListItem(
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_folder)) },
                supportingContent = {
                    Text(folderUri ?: "Nenastaveno", maxLines = 2)
                },
                modifier = Modifier.clickable { folderPicker.launch(null) }
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_section_about))
            ListItem(
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_app_version)) },
                supportingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
            )
        }
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text(stringResource(R.string.settings_mapy_key)) },
            text = {
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    label = { Text(stringResource(R.string.setup_mapy_input)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { prefs.setMapyApiKey(apiKeyDraft.trim()) }
                    showApiKeyDialog = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

private fun maskKey(key: String): String =
    if (key.length <= 8) "••••" else "${key.take(4)}••••${key.takeLast(4)}"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
