package com.example.paperlessmeeting.di

import com.example.paperlessmeeting.data.repository.MeetingRepository

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideRetrofit(): retrofit2.Retrofit {
        return retrofit2.Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8000/")
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
