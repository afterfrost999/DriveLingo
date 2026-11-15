package com.drivelingo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.drivelingo.BuildConfig
import com.drivelingo.core.designsystem.MalchaColors
import com.drivelingo.core.designsystem.MalchaTheme
import com.drivelingo.feature.vehicleconsole.VehicleConsoleOverlay
import com.drivelingo.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val container = (application as DriveLingoApplication).container
        setContent {
            MalchaTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    // Surface 가 기본 글자색(contentColor)을 정한다. 없으면 Text 가 검은색이 된다.
                    Surface(Modifier.fillMaxSize(), color = MalchaColors.Background, contentColor = MalchaColors.TextPrimary) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            AppNavHost()
                            if (BuildConfig.SHOW_VEHICLE_CONSOLE) VehicleConsoleOverlay()
                        }
                    }
                }
            }
        }
    }
}
