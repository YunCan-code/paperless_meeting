package com.example.paperlessmeeting.data.repository

import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.utils.Resource
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class MeetingRepositoryTest {

    private val api = mockk<ApiService>()
    private val repository = MeetingRepositoryImpl(api)

    @Test
    fun `getMeetingById 遇到取消异常时继续抛出`() = runTest {
        coEvery { api.getMeeting(7, 3) } throws CancellationException("the coroutine scope left composition")

        try {
            repository.getMeetingById(7, 3)
            fail("预期应抛出 CancellationException")
        } catch (expected: CancellationException) {
            assertEquals("the coroutine scope left composition", expected.message)
        }
    }

    @Test
    fun `getMeetingById 遇到 http 异常时映射为资源错误`() = runTest {
        coEvery { api.getMeeting(8, 5) } throws httpException(404)

        val result = repository.getMeetingById(8, 5)

        assertTrue(result is Resource.Error)
        assertEquals("HTTP_404", (result as Resource.Error).message)
    }

    @Test
    fun `getMeetingById 遇到普通异常时保留错误消息`() = runTest {
        coEvery { api.getMeeting(9, 6) } throws IllegalStateException("boom")

        val result = repository.getMeetingById(9, 6)

        assertTrue(result is Resource.Error)
        assertEquals("boom", (result as Resource.Error).message)
    }

    private fun httpException(code: Int): HttpException {
        return HttpException(
            Response.error<Any>(
                code,
                "error".toResponseBody("text/plain".toMediaType())
            )
        )
    }
}
