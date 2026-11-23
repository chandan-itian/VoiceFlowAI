package com.voiceflow.ai

import com.voiceflow.ai.data.remote.providers.GroqProvider
import com.voiceflow.ai.data.remote.providers.OpenAIProvider
import com.voiceflow.ai.domain.model.AiProvider
import com.voiceflow.ai.domain.model.AppSettings
import com.voiceflow.ai.domain.model.ProviderType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class GroqFormatterFallbackTest {
    private val models = mutableListOf<String>()
    private val provider = AiProvider("groq", "Groq", ProviderType.GROQ, apiKey = "test")
    private val order = listOf("llama-3.1-8b-instant", "openai/gpt-oss-20b", "llama-3.3-70b-versatile")

    private suspend fun format(vararg statuses: Int): String {
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            requireNotNull(request.body).writeTo(buffer)
            val status = statuses[models.size]
            models += json.parseToJsonElement(buffer.readUtf8()).jsonObject.getValue("model").jsonPrimitive.content
            val body = """{"choices":[{"message":{"role":"assistant","content":"Mujhe knowledge hai."}}]}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status)
                .message("test").body(body.toResponseBody()).build()
        }.build()
        return GroqProvider(OpenAIProvider(client, json)).refineText("mujhe नॉलेज hai", provider, AppSettings())
    }

    @Test fun fallsBackInOrderOnlyAsNeeded() = runTest {
        assertEquals("Mujhe knowledge hai.", format(429, 429, 200))
        assertEquals(order, models)
    }

    @Test fun successUsesOnlyPrimary() = runTest {
        format(200)
        assertEquals(order.take(1), models)
    }

    @Test fun stopsOnInvalidKey() = runTest {
        assertTrue(runCatching { format(401) }.isFailure)
        assertEquals(order.take(1), models)
    }

    @Test fun stopsAfterAllModelsAreLimited() = runTest {
        assertTrue(runCatching { format(429, 429, 429) }.isFailure)
        assertEquals(order, models)
    }
}
