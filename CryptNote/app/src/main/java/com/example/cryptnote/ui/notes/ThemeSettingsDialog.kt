package com.example.cryptnote.ui.notes

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cryptnote.data.preferences.ThemeColor
import com.example.cryptnote.data.preferences.ThemeMode
import com.example.cryptnote.data.preferences.ThemePreferencesManager
import kotlinx.coroutines.launch

@Composable
fun ThemeSettingsDialog(
    themePreferencesManager: ThemePreferencesManager,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentMode by themePreferencesManager.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val currentColor by themePreferencesManager.themeColor.collectAsStateWithLifecycle(
        initialValue = ThemeColor.CYBER_TEAL
    )
    val dynamicColor by themePreferencesManager.dynamicColor.collectAsStateWithLifecycle(initialValue = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Appearance & Theme")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "THEME MODE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                coroutineScope.launch {
                                    themePreferencesManager.setThemeMode(mode)
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = {
                                coroutineScope.launch {
                                    themePreferencesManager.setThemeMode(mode)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> "System Default"
                                ThemeMode.LIGHT -> "Light Mode"
                                ThemeMode.DARK -> "Dark Mode (Cyber Stealth)"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "ACCENT COLOR",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                ThemeColor.entries.forEach { color ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                coroutineScope.launch {
                                    themePreferencesManager.setThemeColor(color)
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentColor == color,
                            onClick = {
                                coroutineScope.launch {
                                    themePreferencesManager.setThemeColor(color)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = color.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dynamic Theming",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Derive colors from Android 12+ wallpaper",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { isEnabled ->
                                coroutineScope.launch {
                                    themePreferencesManager.setDynamicColor(isEnabled)
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
