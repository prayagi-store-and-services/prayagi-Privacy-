package com.example.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Version and Eco route only: this app cannot download or install APKs. */
@Composable
fun UpdateCheckCard(modifier: Modifier = Modifier) {
    val context=LocalContext.current
    var unavailable by remember { mutableStateOf(false) }
    Card(modifier=modifier) {
        Column(Modifier.padding(16.dp)) {
            Text("App updates are managed only by Netra Ecosystem.")
            Text("This app does not download or install updates.")
            OutlinedButton(onClick={ unavailable = !AppUpdater.openEco(context) }) { Text("Open Netra Ecosystem") }
            if(unavailable) Text("Netra Ecosystem is not installed or cannot be opened. Install or open Netra Ecosystem to manage app updates.")
        }
    }
}
