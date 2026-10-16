package es.aria_on_the_planet.quasar

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import es.aria_on_the_planet.quasar.di.AppContainer
import es.aria_on_the_planet.quasar.ui.component.image.loader.newQuasarImageLoader

class QuasarApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        newQuasarImageLoader(context, callFactory = { container.okHttpClient })
}
