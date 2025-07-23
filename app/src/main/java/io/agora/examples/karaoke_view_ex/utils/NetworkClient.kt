package io.agora.examples.karaoke_view_ex.utils

import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object NetworkClient {
    fun sendHttpsRequest(
        url: String,
        headers: Map<*, *>,
        body: String,
        isPost: Boolean,
        call: Callback
    ) {
        val client = OkHttpClient()

        val requestBuilder = Request.Builder().url(url)

        // 添加请求头部
        for ((key, value) in headers) {
            requestBuilder.addHeader(key.toString(), value.toString())
        }

        val request = if (isPost) {
            // 构建请求体
            val requestBody = body.toRequestBody("application/json".toMediaTypeOrNull())
            // 发送POST请求
            requestBuilder.post(requestBody).build()
        } else {
            requestBuilder.get().build()
        }

        client.newCall(request).enqueue(call)
    }
}