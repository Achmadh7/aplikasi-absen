package id.absensi.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.absensi.app.data.DatabaseProvider
import id.absensi.app.data.SupabaseClient
import id.absensi.app.ui.theme.BentoEmerald
import id.absensi.app.ui.theme.BentoIndigo
import kotlinx.coroutines.launch

@Composable
fun SyncStatusPill(
    configured: Boolean,
    pendingCount: Int,
    syncing: Boolean,
    onClick: () -> Unit
) {
    val label = when {
        syncing -> "Sync..."
        !configured -> "Offline"
        pendingCount > 0 -> "$pendingCount pending"
        else -> "Tersinkron"
    }
    val tint = when {
        !configured -> MaterialTheme.colorScheme.onSurfaceVariant
        pendingCount > 0 || syncing -> BentoIndigo
        else -> BentoEmerald
    }
    val icon = when {
        syncing || pendingCount > 0 -> Icons.Filled.CloudSync
        configured -> Icons.Filled.CloudDone
        else -> Icons.Filled.CloudOff
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (syncing) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = tint)
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

@Composable
fun SyncConfigDialog(
    pendingCount: Int,
    onDismiss: () -> Unit,
    onSynced: (String) -> Unit
) {
    val context = LocalContext.current
    val client = remember { SupabaseClient(context) }
    val scope = rememberCoroutineScope()

    var configured by remember { mutableStateOf(client.prefs.isConfigured) }
    var url by remember { mutableStateOf(client.prefs.url) }
    var anonKey by remember { mutableStateOf(client.prefs.anonKey) }
    var email by remember { mutableStateOf(client.prefs.email) }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Sync Multi-HP & Auto-WA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (configured) {
                    Text("Akun: ${client.prefs.email}", style = MaterialTheme.typography.bodyMedium)
                    Text("ID HP: ${client.prefs.deviceId}", style = MaterialTheme.typography.bodySmall)
                    Text("Antrean belum sync: $pendingCount", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Supabase URL") },
                        placeholder = { Text("https://xxxx.supabase.co") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = anonKey,
                        onValueChange = { anonKey = it },
                        label = { Text("Supabase Anon Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Guru") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (configured) {
                Button(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        errorMsg = null
                        scope.launch {
                            val res = client.syncAll(DatabaseProvider.init(context))
                            loading = false
                            res.onSuccess { count ->
                                onSynced("Sync selesai ($count data terkirim)")
                                onDismiss()
                            }.onFailure { e ->
                                errorMsg = e.message ?: "Sync gagal"
                            }
                        }
                    }
                ) {
                    Text(if (loading) "Menyinkronkan..." else "Sync Sekarang")
                }
            } else {
                Button(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        errorMsg = null
                        scope.launch {
                            val loginRes = client.signIn(url, anonKey, email, password)
                            if (loginRes.isFailure) {
                                loading = false
                                errorMsg = loginRes.exceptionOrNull()?.message ?: "Login gagal"
                                return@launch
                            }
                            configured = true
                            val syncRes = client.syncAll(DatabaseProvider.init(context))
                            loading = false
                            syncRes.onSuccess { count ->
                                onSynced("Terhubung & sync selesai ($count data)")
                                onDismiss()
                            }.onFailure { e ->
                                errorMsg = e.message ?: "Login berhasil, sync gagal"
                            }
                        }
                    }
                ) {
                    Text(if (loading) "Menghubungkan..." else "Masuk & Sync")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (configured) {
                    OutlinedButton(
                        enabled = !loading,
                        onClick = {
                            client.prefs.logout()
                            configured = false
                            password = ""
                        }
                    ) {
                        Text("Keluar")
                    }
                }
                TextButton(enabled = !loading, onClick = onDismiss) {
                    Text("Tutup")
                }
            }
        }
    )
}
