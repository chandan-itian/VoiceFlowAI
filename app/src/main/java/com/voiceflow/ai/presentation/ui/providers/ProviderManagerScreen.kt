package com.voiceflow.ai.presentation.ui.providers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voiceflow.ai.domain.model.AiProvider
import com.voiceflow.ai.domain.model.ProviderType
import com.voiceflow.ai.presentation.ui.components.SectionHeader
import com.voiceflow.ai.presentation.ui.components.VoiceFlowTopBar
import com.voiceflow.ai.presentation.viewmodel.ProviderViewModel

@Composable
fun ProviderManagerScreen(
    viewModel: ProviderViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AiProvider?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.testConnectionMessage) {
        state.testConnectionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTestResult()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar("Error: $it")
            viewModel.clearTestResult()
        }
    }

    Scaffold(
        topBar = {
            VoiceFlowTopBar(
                title = "AI Provider Manager",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingProvider = null
                    showEditDialog = true
                }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Provider")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SectionHeader(title = "CONFIGURED CLOUD PROVIDERS")
                Text(
                    text = "API keys are encrypted in Android Keystore (AES-256). They are never shared or logged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(state.providers) { provider ->
                ProviderCard(
                    provider = provider,
                    onEdit = {
                        editingProvider = provider
                        showEditDialog = true
                    },
                    onDelete = { viewModel.deleteProvider(provider.id) },
                    onSetDefault = { viewModel.setDefaultProvider(provider.id) },
                    onTest = { viewModel.testConnection(provider) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }

    if (showEditDialog) {
        ProviderDialog(
            initialProvider = editingProvider,
            onDismiss = { showEditDialog = false },
            onSave = { p ->
                viewModel.saveProvider(p)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: AiProvider,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onTest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (provider.isDefault) {
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
                        Text(text = provider.name, style = MaterialTheme.typography.titleMedium)
                        if (provider.isDefault) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("DEFAULT")
                            }
                        }
                    }
                    Text(
                        text = "Model: ${provider.modelId.ifBlank { "Default" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                }
                if (!provider.isDefault) {
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onTest) {
                    Text("Test Connection")
                }
                if (!provider.isDefault) {
                    TextButton(onClick = onSetDefault) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set Default")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderDialog(
    initialProvider: AiProvider?,
    onDismiss: () -> Unit,
    onSave: (AiProvider) -> Unit
) {
    var name by remember { mutableStateOf(initialProvider?.name ?: "") }
    var apiKey by remember { mutableStateOf(initialProvider?.apiKey ?: "") }
    var baseUrl by remember { mutableStateOf(initialProvider?.baseUrl ?: "") }
    var modelId by remember { mutableStateOf(initialProvider?.modelId ?: "") }
    var selectedType by remember { mutableStateOf(initialProvider?.type ?: ProviderType.GEMINI) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProvider == null) "Add AI Provider" else "Edit Provider") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID") },
                    supportingText = if (selectedType == ProviderType.GROQ) {
                        { Text("Use a Whisper model for audio. Formatting defaults to Llama 3.1 8B Instant.") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = (initialProvider ?: AiProvider(
                        id = "provider_${System.currentTimeMillis()}",
                        name = name.ifBlank { "Custom Provider" },
                        type = selectedType
                    )).copy(
                        name = name.ifBlank { "Custom Provider" },
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        modelId = modelId
                    )
                    onSave(p)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
