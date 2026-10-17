package es.aria_on_the_planet.quasar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import es.aria_on_the_planet.quasar.ui.navigation.QuasarNavHost

/** アプリの唯一の Activity。画面の行き来は [QuasarNavHost] (Navigation 3)。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as QuasarApplication).container
        setContent {
            QuasarAppContent(container) {
                QuasarNavHost(container, onExit = ::finish)
            }
        }
    }
}
