package com.customgeocache.app.ui.caches

import android.content.Intent
import android.net.Uri
import android.text.Html
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.customgeocache.app.data.db.entities.CacheEntity
import com.customgeocache.app.data.db.entities.LogEntity
import com.customgeocache.app.util.Rot13
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheDetailScreen(
    gccode: String,
    onBack: () -> Unit,
    onNavigateToCompass: () -> Unit = {},
    onLog: () -> Unit = {}
) {
    val vm: CacheDetailViewModel = viewModel(factory = CacheDetailViewModel.factory(gccode))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.cache?.name ?: gccode, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !state.refreshing) {
                        if (state.refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                    IconButton(onClick = {
                        vm.navigate()
                        onLog()
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Logovat")
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.geocaching.com/geocache/$gccode"))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val cache = state.cache
        if (cache == null) {
            EmptyOrLoading(state.refreshing, padding)
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HeaderCard(cache, onNavigateToCompass = {
                vm.navigate()
                onNavigateToCompass()
            }, onLog = {
                vm.navigate()
                onLog()
            }) }

            if (!cache.description.isNullOrBlank()) {
                item { SectionTitle("Popis") }
                item { DescriptionCard(cache.description) }
            }

            if (!cache.hint.isNullOrBlank()) {
                item { SectionTitle("Hint") }
                item { HintCard(cache.hint) }
            }

            if (!cache.attributes.isNullOrBlank()) {
                item { SectionTitle("Atributy") }
                item { AttributesRow(cache.attributes) }
            }

            if (state.imageUrls.isNotEmpty()) {
                item { SectionTitle("Obrázky (${state.imageUrls.size})") }
                item { ImagesRow(state.imageUrls) }
            }

            // Logy zobrazujeme vždy — i prázdná sekce se status hláškou,
            // ať uživatel vidí, že to appka aspoň zkusila.
            item {
                SectionTitle(
                    if (state.logs.isNotEmpty()) "Logy (${state.logs.size})"
                    else "Logy"
                )
            }
            if (state.logs.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (state.refreshing)
                                "Načítám logy…"
                            else
                                "Logy se zatím nepodařilo načíst. Klepni nahoře na refresh nebo zkontroluj přihlášení.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.logs, key = { it.id }) { log -> LogCard(log) }
            }
        }
    }
}

@Composable
private fun EmptyOrLoading(refreshing: Boolean, padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        if (refreshing) CircularProgressIndicator()
        else Text("Načítám detail keše…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeaderCard(cache: CacheEntity, onNavigateToCompass: () -> Unit, onLog: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cache.gccode, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.weight(1f))
                Text(cache.type, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.size(4.dp))
            Text(cache.name, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            cache.owner?.let {
                Spacer(Modifier.size(4.dp))
                Text("by $it", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            }

            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("D ${cache.difficulty}") })
                AssistChip(onClick = {}, label = { Text("T ${cache.terrain}") })
                AssistChip(onClick = {}, label = { Text(cache.size) })
            }

            Spacer(Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onNavigateToCompass,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Naviguj")
                }
                OutlinedButton(
                    onClick = onLog,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Logovat")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun DescriptionCard(html: String) {
    val plain = remember(html) {
        @Suppress("DEPRECATION")
        Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = plain,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun HintCard(plainText: String) {
    // Server posílá hint plain text (decrypt=y v URL). UI default ukáže ROT13
    // šifrovaný (jak je zvyk v geocachingu), klepnutí dešifruje na původní text.
    var revealed by remember { mutableStateOf(false) }
    Card(
        onClick = { revealed = !revealed },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lightbulb, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = if (revealed) plainText else Rot13.decode(plainText),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = if (revealed) "Klepni pro skrytí" else "Klepni pro odhalení (ROT13)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AttributesRow(csv: String) {
    val list = csv.split(",").filter { it.isNotBlank() }
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        list.forEach { attr ->
            AssistChip(onClick = {}, label = { Text(attr) })
        }
    }
}

@Composable
private fun ImagesRow(urls: List<String>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(urls, key = { it }) { url ->
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .width(180.dp)
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun LogCard(log: LogEntity) {
    val dateFmt = remember { SimpleDateFormat("d. M. yyyy", Locale.getDefault()) }
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(log.author, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(dateFmt.format(Date(log.dateMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(log.type, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(6.dp))
            Text(log.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
