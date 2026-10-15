package es.aria_on_the_planet.quasar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.aria_on_the_planet.quasar.ui.login.LoginScreen
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import es.aria_on_the_planet.quasar.ui.timeline.TimelineScreen
import es.aria_on_the_planet.quasar.ui.timeline.TimelineViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as QuasarApplication).container
        setContent {
            QuasarAppContent(container) {
                val session by container.sessionStore.session.collectAsStateWithLifecycle()
                val current = session
                if (current == null) {
                    LoginScreen(onLogin = container.sessionStore::save)
                } else {
                    val viewModel: TimelineViewModel = viewModel(
                        key = "${current.serverUrl}|${current.accessToken.hashCode()}",
                        factory = viewModelFactory {
                            initializer { TimelineViewModel(
                                current,
                                container.timelineRepository,
                                container.statusRepository,
                                container.notificationRepository,
                                container.streamingClient,
                            ) }
                        },
                    )
                    TimelineScreen(viewModel = viewModel, onLogout = container.sessionStore::clear)
                }
            }
        }
    }
}
