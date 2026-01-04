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
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)  // 连接超时
            .readTimeout(60, TimeUnit.SECONDS)     // 读取超时 (下载大文件需要更长)
            .writeTimeout(30, TimeUnit.SECONDS)    // 写入超时
            .retryOnConnectionFailure(true)        // 连接失败自动重试
            .build()
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
