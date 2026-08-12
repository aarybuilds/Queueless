package com.iiitl.canteen.ui.suggestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionScreen(
    studentUid: String,
    studentName: String,
    onSubmitSuggestion: suspend (String, String, String) -> Result<Unit>,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSubmitted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val maxChars = 500

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suggestion Box", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Have feedback or a dish request? Let us know!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { newValue ->
                        if (newValue.length <= maxChars) {
                            messageText = newValue
                            isSubmitted = false
                            errorMessage = null
                        }
                    },
                    label = { Text("Your Suggestion") },
                    placeholder = { Text("Type your feedback here...") },
                    minLines = 5,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = "${messageText.length}/$maxChars",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                if (isSubmitted) {
                    Text(
                        text = "Suggestion submitted!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (messageText.isBlank() || isSubmitting) return@Button
                        isSubmitting = true
                        errorMessage = null
                        scope.launch {
                            val result = onSubmitSuggestion(studentUid, studentName, messageText)
                            isSubmitting = false
                            result.fold(
                                onSuccess = {
                                    isSubmitted = true
                                    messageText = ""
                                },
                                onFailure = { err ->
                                    errorMessage = err.message ?: "Failed to submit suggestion."
                                }
                            )
                        }
                    },
                    enabled = messageText.isNotBlank() && !isSubmitting && messageText.length <= maxChars,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text("Submit Suggestion", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
