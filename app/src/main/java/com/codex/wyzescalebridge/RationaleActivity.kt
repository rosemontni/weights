package com.codex.wyzescalebridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.wyzescalebridge.ui.theme.WyzeScaleBridgeTheme

class RationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WyzeScaleBridgeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Why this app asks for Health Connect access",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "Wyze Scale Bridge only requests permission to write weight and body-fat records that you imported from your own Wyze CSV export.",
                        )
                    }
                }
            }
        }
    }
}
