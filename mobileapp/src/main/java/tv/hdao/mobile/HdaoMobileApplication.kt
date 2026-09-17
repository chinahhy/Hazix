package tv.hdao.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import tv.hdao.app.data.NetworkClients

class HdaoMobileApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(NetworkClients.httpClient)
        .crossfade(true)
        .build()
}
