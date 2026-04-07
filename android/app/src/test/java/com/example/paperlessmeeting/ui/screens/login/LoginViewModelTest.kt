package com.example.paperlessmeeting.ui.screens.login

import com.example.paperlessmeeting.MainDispatcherRule
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.repository.MeetingRepository
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<MeetingRepository>()
    private val userPreferences = mockk<UserPreferences>(relaxed = true)
    private val appSettingsState = mockk<AppSettingsState>(relaxed = true)

    @Test
    fun `空输入时显示姓名或手机号提示`() = runTest {
        val viewModel = createViewModel()

        viewModel.login("   ")
        advanceUntilIdle()

        assertEquals("请输入姓名或手机号", awaitErrorMessage(viewModel))
    }

    @Test
    fun `401 登录异常会映射为友好提示`() = runTest {
        coEvery { repository.login(any()) } throws httpException(401)

        val viewModel = createViewModel()
        viewModel.login("张三")

        val message = awaitErrorMessage(viewModel)
        assertEquals("用户不存在，请检查姓名或手机号", message)
        assertFalse(message.contains("401"))
    }

    @Test
    fun `300 登录异常会提示使用手机号`() = runTest {
        coEvery { repository.login(any()) } throws IllegalStateException("HTTP 300")

        val viewModel = createViewModel()
        viewModel.login("张三")

        assertEquals("存在重名，请使用手机号", awaitErrorMessage(viewModel))
    }

    @Test
    fun `未知异常会显示通用失败提示`() = runTest {
        coEvery { repository.login(any()) } throws IllegalStateException("boom")

        val viewModel = createViewModel()
        viewModel.login("张三")

        val message = awaitErrorMessage(viewModel)
        assertEquals("登录失败，请稍后重试", message)
        assertFalse(message.contains("401"))
    }

    private fun createViewModel(): LoginViewModel {
        return LoginViewModel(
            repository = lazyOf(repository),
            userPreferences = lazyOf(userPreferences),
            appSettingsState = appSettingsState
        )
    }

    private fun TestScope.awaitErrorMessage(viewModel: LoginViewModel): String {
        repeat(50) {
            advanceUntilIdle()
            Thread.sleep(10)
            advanceUntilIdle()
            val currentState = viewModel.uiState.value
            if (currentState is LoginUiState.Error) {
                return currentState.message
            }
        }
        fail("预期 uiState 最终进入 Error，实际是 ${viewModel.uiState.value}")
        throw AssertionError("unreachable")
    }

    private fun httpException(code: Int): HttpException {
        return HttpException(
            Response.error<Any>(
                code,
                "error".toResponseBody("text/plain".toMediaType())
            )
        )
    }

    private fun <T> lazyOf(value: T): Lazy<T> {
        return object : Lazy<T> {
            override fun get(): T = value
        }
    }
}
