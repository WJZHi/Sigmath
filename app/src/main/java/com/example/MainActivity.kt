package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme

class MainActivity : AppCompatActivity() {
    private val viewModel: MathViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode = viewModel.themeMode
            val isDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                ParallaxSwipeBackNavHost(
                    viewModel = viewModel,
                    onOpenHistory = {
                        HistoryBottomSheetDialogFragment.newInstance()
                            .show(supportFragmentManager, HistoryBottomSheetDialogFragment.TAG)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
