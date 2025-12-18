package com.example.paperlessmeeting.di

import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.data.repository.MockMeetingRepository
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
    fun provideMeetingRepository(): MeetingRepository {
        return MockMeetingRepository()
    }
}
