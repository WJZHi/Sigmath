package com.example

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OpenSourceLibrary(
    val name: String,
    val developer: String,
    val license: String,
    val repositoryUrl: String
)

val openSourceLibraries = listOf(
    OpenSourceLibrary(
        name = "Jetpack Compose",
        developer = "Google / AOSP",
        license = "Apache-2.0",
        repositoryUrl = "https://developer.android.com/jetpack/compose"
    ),
    OpenSourceLibrary(
        name = "Material 3",
        developer = "Google, Inc.",
        license = "Apache-2.0",
        repositoryUrl = "https://github.com/material-components/material-components-android"
    ),
    OpenSourceLibrary(
        name = "Room Database",
        developer = "Google / AOSP",
        license = "Apache-2.0",
        repositoryUrl = "https://developer.android.com/training/data-storage/room"
    ),
    OpenSourceLibrary(
        name = "KotlinX Coroutines",
        developer = "JetBrains",
        license = "Apache-2.0",
        repositoryUrl = "https://github.com/Kotlin/kotlinx.coroutines"
    ),
    OpenSourceLibrary(
        name = "AndroidX Lifecycle",
        developer = "Google / AOSP",
        license = "Apache-2.0",
        repositoryUrl = "https://developer.android.com/jetpack/androidx"
    ),
    OpenSourceLibrary(
        name = "Retrofit",
        developer = "Square, Inc.",
        license = "Apache-2.0",
        repositoryUrl = "https://github.com/square/retrofit"
    ),
    OpenSourceLibrary(
        name = "OkHttp",
        developer = "Square, Inc.",
        license = "Apache-2.0",
        repositoryUrl = "https://github.com/square/okhttp"
    ),
    OpenSourceLibrary(
        name = "Moshi",
        developer = "Square, Inc.",
        license = "Apache-2.0",
        repositoryUrl = "https://github.com/square/moshi"
    ),
    OpenSourceLibrary(
        name = "Robolectric",
        developer = "Robolectric",
        license = "MIT",
        repositoryUrl = "https://github.com/robolectric/robolectric"
    ),
    OpenSourceLibrary(
        name = "Roborazzi",
        developer = "takahirom",
        license = "Apache-2.0",
        repositoryUrl = "https://github.com/takahirom/roborazzi"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredList = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            openSourceLibraries
        } else {
            val q = searchQuery.trim().lowercase()
            openSourceLibraries.filter {
                it.name.lowercase().contains(q) ||
                it.developer.lowercase().contains(q) ||
                it.license.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "开源许可协议",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Compact Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("搜索开源项目或机构...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )
            }

            // Compact Libraries List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.name }) { lib ->
                    OpenSourceLibraryItem(
                        library = lib,
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(lib.repositoryUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开网页链接", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun OpenSourceLibraryItem(
    library: OpenSourceLibrary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = library.developer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = library.license,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Launch,
                    contentDescription = "查看代码库",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
