package com.voiceflow.ai.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.voiceflow.ai.presentation.navigation.VoiceFlowNavGraph
import com.voiceflow.ai.presentation.ui.theme.VoiceFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VoiceFlowTheme {
                val navController = rememberNavController()
                VoiceFlowNavGraph(navController = navController)
            }
        }
    }
}
