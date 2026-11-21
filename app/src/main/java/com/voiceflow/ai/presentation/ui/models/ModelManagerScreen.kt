package com.voiceflow.ai.presentation.ui.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voiceflow.ai.presentation.ui.components.SectionHeader
import com.voiceflow.ai.presentation.ui.components.VoiceFlowTopBar
import com.voiceflow.ai.presentation.viewmodel.ModelItemUiState
import com.voiceflow.ai.presentation.viewmodel.ModelViewModel

@Composable
fun ModelManagerScreen(
    viewModel: ModelViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            VoiceFlowTopBar(
                title = "Whisper Model Manager",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SectionHeader(title = "AVAILABLE ON-DEVICE MODELS")
                Text(
                    text = "Models run entirely offline on your Snapdragon 695. No data leaves your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(state.models) { item ->
                ModelCard(
                    item = item,
                    onDownload = { viewModel.downloadModel(context, item.model) },
                    onDelete = { viewModel.deleteModel(item.model) },
                    onActivate = { viewModel.setActiveModel(item.model) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ModelCard(
    item: ModelItemUiState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.model.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (item.isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("ACTIVE")
                            }
                        }
                    }
                    Text(
                        text = item.model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.isDownloaded) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!item.isDownloaded) {
                    Button(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download (~${item.model.sizeBytes / 1_000_000} MB)")
                    }
                } else if (!item.isActive) {
                    FilledTonalButton(onClick = onActivate) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use as Active Model")
                    }
                } else {
                    Text(
                        text = "Loaded & Ready",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
