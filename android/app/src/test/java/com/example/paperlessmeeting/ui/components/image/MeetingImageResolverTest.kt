package com.example.paperlessmeeting.ui.components.image

import com.example.paperlessmeeting.domain.model.Meeting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeetingImageResolverTest {

    @Test
    fun `会议卡片优先缩略图`() {
        val meeting = testMeeting(
            cardImageUrl = "https://example.com/original.jpg",
            cardImageThumbUrl = "https://example.com/thumb.jpg"
        )

        val model = MeetingImageResolver.resolve(meeting, AppImageSlot.MeetingCard)

        assertEquals("https://example.com/thumb.jpg", model.data)
        assertEquals(
            "meeting-card:7:https://example.com/thumb.jpg",
            model.memoryCacheKey
        )
    }

    @Test
    fun `会议横幅优先原图且不复用缩略图占位`() {
        val meeting = testMeeting(
            cardImageUrl = "https://example.com/original.jpg",
            cardImageThumbUrl = "https://example.com/thumb.jpg"
        )

        val model = MeetingImageResolver.resolve(meeting, AppImageSlot.MeetingHero)

        assertEquals("https://example.com/original.jpg", model.data)
        assertNull(model.placeholderMemoryCacheKey)
        assertFalse(model.allowHardware)
    }

    @Test
    fun `会议图片缺失时返回占位模型`() {
        val meeting = testMeeting(cardImageUrl = null, cardImageThumbUrl = null)

        val model = MeetingImageResolver.resolve(meeting, AppImageSlot.MeetingCard)

        assertNull(model.data)
        assertNull(model.memoryCacheKey)
        assertEquals(AppImageFallback.Meeting, model.fallback)
    }

    @Test
    fun `媒体网格在缩略图缺失时回退预览图`() {
        val item = com.example.paperlessmeeting.domain.model.MediaItem(
            id = 9,
            kind = "image",
            title = "Agenda",
            parentId = null,
            extension = "png",
            previewUrl = "/static/media/agenda.png",
            thumbnailUrl = null
        )

        val model = MediaImageResolver.resolveGrid(item, "https://coso.top/static/")

        assertEquals("https://coso.top/static/media/agenda.png", model.data)
        assertEquals(
            "media-grid:9:https://coso.top/static/media/agenda.png",
            model.memoryCacheKey
        )
    }

    private fun testMeeting(
        cardImageUrl: String?,
        cardImageThumbUrl: String?
    ): Meeting {
        return Meeting(
            id = 7,
            title = "Weekly Review",
            meetingTypeId = 1,
            startTime = "2026-03-27 09:00:00",
            endTime = "2026-03-27 10:00:00",
            location = "A100",
            host = null,
            cardImageUrl = cardImageUrl,
            cardImageThumbUrl = cardImageThumbUrl
        )
    }
}
