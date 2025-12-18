package com.example.paperlessmeeting.data.repository

import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.MeetingStatus
import com.example.paperlessmeeting.domain.model.MeetingType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

interface MeetingRepository {
    fun getMeetings(): Flow<List<Meeting>>
    suspend fun getMeetingById(id: String): Meeting?
}

@Singleton
class MockMeetingRepository @Inject constructor() : MeetingRepository {
    private val mockData = listOf(
        Meeting(
            id = "1",
            title = "2025年度 Q1 战略复盘会",
            type = MeetingType.Review,
            status = MeetingStatus.Ongoing,
            startTime = "14:00",
            endTime = "16:00",
            location = "101 核心会议室",
            host = "张总",
            imageUrl = "https://images.unsplash.com/photo-1517048676732-d65bc937f952?q=80&w=2070&auto=format&fit=crop"
        ),
        Meeting(
            id = "2",
            title = "安卓端 UI 交互评审",
            type = MeetingType.Urgent,
            status = MeetingStatus.Upcoming,
            startTime = "16:30",
            endTime = "17:30",
            location = "305 研发讨论区",
            host = "技术部",
            imageUrl = "https://images.unsplash.com/photo-1626245037145-5d4715f33339?q=80&w=2070&auto=format&fit=crop"
        ),
        Meeting(
            id = "3",
            title = "每周例行进度同步",
            type = MeetingType.Weekly,
            status = MeetingStatus.Upcoming,
            startTime = "明天 09:30",
            endTime = "10:30",
            location = "线上会议 (Zoom)",
            host = "项目办",
            imageUrl = null
        ),
        Meeting(
            id = "4",
            title = "新员工入职启动会",
            type = MeetingType.Kickoff,
            status = MeetingStatus.Finished,
            startTime = "昨天 14:00",
            endTime = "15:00",
            location = "多功能厅",
            host = "HRBP"
        ),
        Meeting(
            id = "5",
            title = "服务器迁移演练总结",
            type = MeetingType.General,
            status = MeetingStatus.Finished,
            startTime = "昨天 10:00",
            endTime = "11:30",
            location = "202 会议室",
            host = "运维组"
        )
    )

    override fun getMeetings(): Flow<List<Meeting>> = flow {
        delay(1000)
        emit(mockData)
    }

    override suspend fun getMeetingById(id: String): Meeting? {
        delay(500)
        return mockData.find { it.id == id }
    }
}
