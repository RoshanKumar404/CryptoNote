package com.example.cryptnote.ui.notes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.SyncStatus

data class SyncStatusInfo(
    val icon: ImageVector,
    val color: Color,
    val label: String,
    val containerColor: Color,
    val title: String,
    val description: String
)

@Composable
fun getSyncStatusInfo(status: SyncStatus): SyncStatusInfo {
    return when (status) {
        SyncStatus.PENDING -> SyncStatusInfo(
            icon = Icons.Default.Schedule,
            color = MaterialTheme.colorScheme.secondary,
            label = "Pending",
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            title = "Sync Pending (Offline)",
            description = "Changes are saved locally on your device and encrypted with zero-knowledge AES-256-GCM.\n\nWaiting for an active internet connection to securely upload to your cloud vault."
        )
        SyncStatus.SYNCING -> SyncStatusInfo(
            icon = Icons.Default.Sync,
            color = MaterialTheme.colorScheme.tertiary,
            label = "Syncing",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            title = "Syncing in Progress",
            description = "Currently encrypting and synchronizing changes with your remote cloud vault over an encrypted TLS connection."
        )
        SyncStatus.SYNCED -> SyncStatusInfo(
            icon = Icons.Default.CheckCircle,
            color = MaterialTheme.colorScheme.primary,
            label = "Synced",
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            title = "Fully Synchronized",
            description = "This note is safely encrypted and backed up in your cloud vault. Local and cloud copies are identical and up-to-date."
        )
        SyncStatus.CONFLICT -> SyncStatusInfo(
            icon = Icons.Default.Warning,
            color = MaterialTheme.colorScheme.error,
            label = "Conflict",
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            title = "Sync Conflict Detected",
            description = "A conflicting update was detected from another device or session.\n\nYour local edits have been preserved in this note. Tap 'Resolve Conflict' to review differences and choose which version to keep."
        )
        SyncStatus.FAILED -> SyncStatusInfo(
            icon = Icons.Default.Warning,
            color = MaterialTheme.colorScheme.error,
            label = "Failed",
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            title = "Sync Failed",
            description = "The note could not be synced due to a network or server error. The background sync worker will automatically retry with exponential backoff."
        )
    }
}

@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val info = getSyncStatusInfo(status)
    val infiniteTransition = rememberInfiniteTransition(label = "syncRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val iconModifier = if (status == SyncStatus.SYNCING) {
        Modifier
            .size(13.dp)
            .rotate(rotationAngle)
    } else {
        Modifier.size(13.dp)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(info.containerColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = info.label,
                tint = info.color,
                modifier = iconModifier
            )
            Text(
                text = info.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = info.color
            )
        }
    }
}

@Composable
fun SyncStatusDetailDialog(
    note: Note,
    onDismiss: () -> Unit,
    onResolveConflict: (() -> Unit)? = null
) {
    val info = getSyncStatusInfo(note.syncStatus)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = info.icon,
                contentDescription = info.label,
                tint = info.color,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = info.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Note: ${note.title.ifBlank { "Untitled" }}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version: ${note.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Updated: ${formatTimestamp(note.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (note.syncStatus == SyncStatus.CONFLICT && onResolveConflict != null) {
                Button(
                    onClick = onResolveConflict,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Resolve Conflict")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Got it")
                }
            }
        },
        dismissButton = {
            if (note.syncStatus == SyncStatus.CONFLICT && onResolveConflict != null) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun ConflictWarningBanner(
    conflictCount: Int,
    onReviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Conflict warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "$conflictCount Sync Conflict${if (conflictCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Conflicting updates require resolution",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onReviewClick,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Resolve", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
