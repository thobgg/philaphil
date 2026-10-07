package de.bgghome.philaphil.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import de.bgghome.philaphil.Plattform
import okio.Path.Companion.toOkioPath
import java.io.File

/** Bilder von Commons ueber den HTTP-Client der Plattform (User-Agent) mit eigenem Platten-Cache, damit sie offline bleiben. */
fun philaImageLoader(context: PlatformContext, plattform: Plattform): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { plattform.http })) }
        .diskCache {
            DiskCache.Builder()
                .directory(File(plattform.cacheOrdner, "bilder").apply { mkdirs() }.toOkioPath())
                .maxSizeBytes(512L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
