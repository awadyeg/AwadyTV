package com.awadytv.core.network

import android.util.Log
import com.awadytv.BuildConfig
import com.awadytv.data.xtream.XtreamApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

const val USER_AGENT = "AwadyTV/1.0 (Linux; Android TV)"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = XtreamJson

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val withUa = if (request.header("User-Agent") == null) request.newBuilder().header("User-Agent", USER_AGENT).build() else request
            chain.proceed(withUa)
        })
        .apply { if (BuildConfig.DEBUG) addInterceptor(RedactingLogInterceptor()) }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/") // every Xtream call passes an absolute @Url
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideXtreamApi(retrofit: Retrofit): XtreamApi = retrofit.create(XtreamApi::class.java)
}

/**
 * Debug-only request log. Logs host + action and the status code; never the path segments of stream
 * URLs or the username/password query parameters.
 */
class RedactingLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val url = request.url
        val action = url.queryParameter("action") ?: "auth"
        val start = System.nanoTime()
        val response = chain.proceed(request)
        if (url.encodedPath.endsWith("player_api.php")) {
            Log.d("AwadyNet", "${request.method} ${url.host}:${url.port} action=$action -> ${response.code} (${(System.nanoTime() - start) / 1_000_000}ms)")
        }
        return response
    }
}
