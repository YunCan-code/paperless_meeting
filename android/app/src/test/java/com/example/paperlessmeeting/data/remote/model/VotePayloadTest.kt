package com.example.paperlessmeeting.data.remote.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VotePayloadTest {

    private val gson = Gson()

    @Test
    fun `selected_option_ids 缺失时映射为空列表`() {
        val payload = gson.fromJson(
            """
            {
              "id": 1,
              "meeting_id": 2,
              "title": "测试投票",
              "is_multiple": false,
              "is_anonymous": true,
              "max_selections": 1,
              "duration_seconds": 60,
              "status": "active",
              "created_at": "2026-04-01T20:00:00",
              "options": []
            }
            """.trimIndent(),
            VotePayload::class.java
        )

        assertTrue(payload.toDomain().selected_option_ids.isEmpty())
    }

    @Test
    fun `selected_option_ids 为 null 时映射为空列表`() {
        val payload = gson.fromJson(
            """
            {
              "id": 1,
              "meeting_id": 2,
              "title": "测试投票",
              "is_multiple": false,
              "is_anonymous": true,
              "max_selections": 1,
              "duration_seconds": 60,
              "status": "active",
              "created_at": "2026-04-01T20:00:00",
              "options": [],
              "selected_option_ids": null
            }
            """.trimIndent(),
            VotePayload::class.java
        )

        assertTrue(payload.toDomain().selected_option_ids.isEmpty())
    }

    @Test
    fun `selected_option_ids 为空数组时保持为空列表`() {
        val payload = gson.fromJson(
            """
            {
              "id": 1,
              "meeting_id": 2,
              "title": "测试投票",
              "is_multiple": false,
              "is_anonymous": true,
              "max_selections": 1,
              "duration_seconds": 60,
              "status": "active",
              "created_at": "2026-04-01T20:00:00",
              "options": [],
              "selected_option_ids": []
            }
            """.trimIndent(),
            VotePayload::class.java
        )

        assertTrue(payload.toDomain().selected_option_ids.isEmpty())
    }

    @Test
    fun `selected_option_ids 有值时保持原样`() {
        val payload = gson.fromJson(
            """
            {
              "id": 1,
              "meeting_id": 2,
              "title": "测试投票",
              "is_multiple": true,
              "is_anonymous": false,
              "max_selections": 2,
              "duration_seconds": 60,
              "status": "active",
              "created_at": "2026-04-01T20:00:00",
              "options": [],
              "selected_option_ids": [1, 2]
            }
            """.trimIndent(),
            VotePayload::class.java
        )

        assertEquals(listOf(1, 2), payload.toDomain().selected_option_ids)
    }
}
