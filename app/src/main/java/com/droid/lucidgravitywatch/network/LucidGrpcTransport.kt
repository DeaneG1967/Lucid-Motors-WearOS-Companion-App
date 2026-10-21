package com.droid.lucidgravitywatch.network

import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.protocol.LucidProtobufCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class LucidApiException(
    val grpcStatus: Int,
    override val message: String,
    cause: Throwable? = null
) : IOException(message, cause)

interface LucidGrpcTransport {
    suspend fun unaryCall(
        region: LucidRegion,
        serviceMethod: String,
        requestProtobuf: ByteArray,
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): ByteArray
}

/**
 * Production HTTP/2 TLS gRPC client communicating directly with Lucid Motors Mobile Gateway
 * (`mobile.deneb.prod.infotainment.pdx.atieva.com`, `mobile.do.prod.eu.lcid.io`, etc.).
 *
 * Uses a streaming gRPC [RequestBody] (`contentLength() == -1L`) and a network-level header
 * sanitizer so `Content-Length` and `Accept-Encoding` are never injected onto HTTP/2 `HEADERS`
 * frames. Lucid's Mobile Gateway forwards incoming metadata to its internal authentication
 * service; if an outer `content-length` header is present, the upstream HTTP/2 server rejects
 * the proxied stream with `RST_STREAM` (`PROTOCOL_ERROR`).
 */
class OkHttpLucidGrpcTransport(
    httpClient: OkHttpClient = defaultOkHttpClient(),
    private val baseUrlOverride: String? = null
) : LucidGrpcTransport {

    private val client: OkHttpClient = if (httpClient.networkInterceptors.contains(GRPC_HEADER_SANITIZER)) {
        httpClient
    } else {
        httpClient.newBuilder()
            .addNetworkInterceptor(GRPC_HEADER_SANITIZER)
            .build()
    }

    override suspend fun unaryCall(
        region: LucidRegion,
        serviceMethod: String,
        requestProtobuf: ByteArray,
        bearerToken: String?,
        extraHeaders: Map<String, String>
    ): ByteArray = withContext(Dispatchers.IO) {
        val normalizedMethod = if (serviceMethod.startsWith("/")) serviceMethod else "/$serviceMethod"
        val url = if (baseUrlOverride != null) {
            "${baseUrlOverride.trimEnd('/')}$normalizedMethod"
        } else {
            "https://${region.apiHost}$normalizedMethod"
        }

        val framedPayload = LucidProtobufCodec.encodeGrpcFrame(requestProtobuf)
        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType = GRPC_MEDIA_TYPE
            override fun contentLength(): Long = -1L
            override fun writeTo(sink: BufferedSink) {
                sink.write(framedPayload)
            }
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("content-type", "application/grpc")
            .header("te", "trailers")
            .header("accept-encoding", "identity")
            .header("grpc-accept-encoding", "identity,gzip")
            .header("user-agent", "grpc-python-asyncio/1.75.1 LucidGravityWatch/1.0.1")

        if (!bearerToken.isNullOrBlank()) {
            requestBuilder.header("authorization", "Bearer $bearerToken")
        }
        for ((k, v) in extraHeaders) {
            requestBuilder.header(k, v)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw LucidApiException(
                    grpcStatus = response.code,
                    message = "Lucid Cloud HTTP ${response.code}: ${response.message}"
                )
            }

            val bodyBytes = response.body?.bytes() ?: ByteArray(0)

            val trailerStatus = runCatching { response.trailers()["grpc-status"] }.getOrNull()
            val headerStatus = response.header("grpc-status")
            val grpcStatus = (trailerStatus ?: headerStatus)?.toIntOrNull() ?: 0

            if (grpcStatus != 0) {
                val rawMessage = runCatching { response.trailers()["grpc-message"] }.getOrNull()
                    ?: response.header("grpc-message")
                    ?: "gRPC status code $grpcStatus"
                val decodedMessage = runCatching {
                    URLDecoder.decode(rawMessage, "UTF-8")
                }.getOrDefault(rawMessage)
                throw LucidApiException(
                    grpcStatus = grpcStatus,
                    message = "Lucid Cloud API Error ($grpcStatus): $decodedMessage"
                )
            }

            LucidProtobufCodec.decodeGrpcFrame(bodyBytes)
        }
    }

    companion object {
        private val GRPC_MEDIA_TYPE = "application/grpc".toMediaType()

        private val GRPC_HEADER_SANITIZER = Interceptor { chain ->
            val sanitized = chain.request().newBuilder()
                .removeHeader("Content-Length")
                .removeHeader("Accept-Encoding")
                .build()
            chain.proceed(sanitized)
        }

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addNetworkInterceptor(GRPC_HEADER_SANITIZER)
                .build()
        }
    }
}
