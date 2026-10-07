package de.bgghome.philaphil

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import de.bgghome.philaphil.ui.philaImageLoader

class PhilaApp : Application(), SingletonImageLoader.Factory {
    lateinit var plattform: AndroidPlattform
        private set

    override fun onCreate() {
        super.onCreate()
        plattform = AndroidPlattform(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = philaImageLoader(context, plattform)
}
