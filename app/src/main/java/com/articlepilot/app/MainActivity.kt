package com.articlepilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArticlePilotTheme {
                ArticlePilotShell()
            }
        }
    }
}

@Composable
private fun ArticlePilotShell() {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "ArticlePilot",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Article → Images → Preview → Publish",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Fondasi aplikasi telah disiapkan. Modul produksi akan diimplementasikan secara bertahap; browser automation belum aktif.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ArticlePilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
