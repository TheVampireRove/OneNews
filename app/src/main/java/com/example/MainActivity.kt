package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.example.ui.NewsViewModel
import com.example.ui.OneNewsApp
import com.example.ui.theme.OneNewsTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val windowInfoTracker = WindowInfoTracker.getOrCreate(this)

        setContent {
            val viewModel: NewsViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val windowSizeClass = calculateWindowSizeClass(this)

            val layoutInfoFlow = remember(this) {
                windowInfoTracker.windowLayoutInfo(this)
            }
            val windowLayoutInfo by layoutInfoFlow.collectAsStateWithLifecycle(
                initialValue = WindowLayoutInfo(emptyList()),
            )
            val foldingFeature = windowLayoutInfo.displayFeatures
                .filterIsInstance<FoldingFeature>()
                .firstOrNull()

            OneNewsTheme(themeSettings = uiState.themeSettings) {
                OneNewsApp(
                    uiState = uiState,
                    windowSizeClass = windowSizeClass,
                    foldingFeature = foldingFeature,
                    viewModel = viewModel,
                )
            }
        }
    }
}
