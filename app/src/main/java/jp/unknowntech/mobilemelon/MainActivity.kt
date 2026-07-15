package jp.unknowntech.mobilemelon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import jp.unknowntech.mobilemelon.ui.MobileMelonApp
import jp.unknowntech.mobilemelon.ui.theme.MobileMelonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileMelonTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MobileMelonApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
