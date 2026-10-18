package com.example.cryptnote.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cryptnote.domain.model.ConflictPair
import com.example.cryptnote.domain.model.ConflictResolutionAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interactive dialog allowing users to inspect and resolve synchronization conflicts
 * between their local edits and changes made from other devices on the cloud.
 */
@Composable
fun ConflictResolutionDialog(
    conflictPair: ConflictPair,
    onResolve: (ConflictResolutionAction) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isManualMergeMode by remember { mutableStateOf(false) }

    val initialMergedTitle = conflictPair.localNote.title.removePrefix("(conflict)").removePrefix("(Conflict)").trim()
    val initialMergedBody = buildString {
        appendLine("--- LOCAL VERSION ---")
        appendLine(conflictPair.localNote.body)
        appendLine()
        appendLine("--- SERVER VERSION ---")
        append(conflictPair.serverNote.body)
    }

    var mergedTitle by remember { mutableStateOf(initialMergedTitle) }
    var mergedBody by remember { mutableStateOf(initialMergedBody) }

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Conflict",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = if (isManualMergeMode) "Merge Conflicting Note" else "Resolve Sync Conflict",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (!isManualMergeMode) {
                    Text(
                        text = "Changes were made to this note from another device. Select which version to keep, or merge both versions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TabRow(selectedTabIndex = selectedTabIndex) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Local Version", maxLines = 1) }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Server Version", maxLines = 1) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val activeNote = if (selectedTabIndex == 0) conflictPair.localNote else conflictPair.serverNote
                    val badgeLabel = if (selectedTabIndex == 0) "This Device (Unsent Edits)" else "Cloud Server (Remote)"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                )
                                Text(
                                    text = "v${activeNote.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = activeNote.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = activeNote.body.ifBlank { "(Empty note body)" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Modified: ${dateFormatter.format(Date(activeNote.updatedAt))}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    // Manual merge editor view
                    Text(
                        text = "Edit the combined note below to finalize the merged content:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = mergedTitle,
                        onValueChange = { mergedTitle = it },
                        label = { Text("Merged Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = mergedBody,
                        onValueChange = { mergedBody = it },
                        label = { Text("Merged Body") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (!isManualMergeMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { isManualMergeMode = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Merge", maxLines = 1)
                    }

                    Button(
                        onClick = {
                            if (selectedTabIndex == 0) {
                                onResolve(ConflictResolutionAction.KeepLocal)
                            } else {
                                onResolve(ConflictResolutionAction.KeepServer)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text(
                            text = if (selectedTabIndex == 0) "Keep Local" else "Keep Server",
                            maxLines = 1
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { isManualMergeMode = false }) {
                        Text("Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onResolve(
                                ConflictResolutionAction.CustomMerge(
                                    mergedTitle = mergedTitle.trim(),
                                    mergedBody = mergedBody.trim()
                                )
                            )
                        }
                    ) {
                        Text("Save Merge")
                    }
                }
            }
        },
        dismissButton = {
            if (!isManualMergeMode) {
                TextButton(onClick = onDismiss) {
                    Text("Decide Later")
                }
            }
        }
    )
}
