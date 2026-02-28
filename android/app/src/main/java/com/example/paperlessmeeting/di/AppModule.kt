package com.example.paperlessmeeting.di

import com.example.paperlessmeeting.data.repository.MeetingRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * OkHttp 客户端配置 - 高并发优化
     * - 增加超时时间，防止网络拥堵时过早放弃
     * - 启用自动重试
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(userPreferences: com.example.paperlessmeeting.data.local.UserPreferences): OkHttpClient {
        try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )

            // Install the all-trusting trust manager
            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory = sslContext.socketFactory

            return OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val requestBuilder = original.newBuilder()
                    userPreferences.getToken()?.let { token ->
                        requestBuilder.header("Authorization", "Bearer $token")
                    }
                    chain.proceed(requestBuilder.build())
                }
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true } // Trust all hostnames
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): retrofit2.Retrofit {
        return retrofit2.Retrofit.Builder()
            .baseUrl("https://coso.top/api/")
            .client(okHttpClient)  // 使用自定义 OkHttp 客户端
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: retrofit2.Retrofit): com.example.paperlessmeeting.data.remote.ApiService {
        return retrofit.create(com.example.paperlessmeeting.data.remote.ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideMeetingRepository(api: com.example.paperlessmeeting.data.remote.ApiService): MeetingRepository {
        return com.example.paperlessmeeting.data.repository.MeetingRepositoryImpl(api)
    }
}
