package dev.claudiocodigo.nexo.data.caldav

import dev.claudiocodigo.nexo.domain.caldav.CalDavCredentials
import dev.claudiocodigo.nexo.domain.caldav.CalDavWriteClient
import dev.claudiocodigo.nexo.domain.caldav.ConditionalCreate
import dev.claudiocodigo.nexo.domain.caldav.ConditionalUpdate
import dev.claudiocodigo.nexo.domain.caldav.WriteOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NextcloudCalDavWriteClient @Inject constructor() : CalDavWriteClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val mediaType = "text/calendar; charset=utf-8".toMediaType()

    override suspend fun create(request: ConditionalCreate, credentials: CalDavCredentials): WriteOutcome =
        withContext(Dispatchers.IO) {
            executePut(
                url = request.targetHref,
                icsPayload = request.icsPayload,
                preconditionHeader = "If-None-Match" to "*",
                credentials = credentials,
                isCreate = true
            )
        }

    override suspend fun update(request: ConditionalUpdate, credentials: CalDavCredentials): WriteOutcome =
        withContext(Dispatchers.IO) {
            val quotedEtag = if (request.baseEtag.startsWith("\"")) request.baseEtag else "\"${request.baseEtag}\""
            executePut(
                url = request.targetHref,
                icsPayload = request.icsPayload,
                preconditionHeader = "If-Match" to quotedEtag,
                credentials = credentials,
                isCreate = false
            )
        }

    private fun executePut(
        url: String,
        icsPayload: String,
        preconditionHeader: Pair<String, String>,
        credentials: CalDavCredentials,
        isCreate: Boolean
    ): WriteOutcome {
        val targetUri = URI(url)
        val expectedServerUri = URI(credentials.server)

        if (!targetUri.scheme.equals(expectedServerUri.scheme, ignoreCase = true) ||
            !targetUri.host.equals(expectedServerUri.host, ignoreCase = true)
        ) {
            return WriteOutcome.PermanentFailure(url, 400, "URL de destino fora da mesma origem configurada")
        }

        val authHeader = buildAuthHeader(credentials)

        val requestBuilder = Request.Builder()
            .url(url)
            .put(icsPayload.toByteArray(Charsets.UTF_8).toRequestBody(mediaType))
            .header("Authorization", authHeader)
            .header(preconditionHeader.first, preconditionHeader.second)

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val newEtag = response.header("ETag")?.trim()

                when (response.code) {
                    200, 201, 204 -> {
                        if (isCreate) WriteOutcome.Created(url, newEtag)
                        else WriteOutcome.Updated(url, newEtag)
                    }
                    412 -> WriteOutcome.Conflict(url, 412, "Precondição falhou: recurso modificado no servidor (412)")
                    401, 403 -> WriteOutcome.PermissionDenied(url, response.code, "Permissão negada ou autenticação necessária (${response.code})")
                    else -> WriteOutcome.PermanentFailure(url, response.code, "Falha na publicação CalDAV: HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            WriteOutcome.TransientFailure(url, e.message ?: "Erro de rede ao publicar no CalDAV", e)
        } catch (e: Exception) {
            WriteOutcome.PermanentFailure(url, 500, e.message ?: "Erro inesperado ao publicar no CalDAV")
        }
    }

    private fun buildAuthHeader(credentials: CalDavCredentials): String {
        val password = credentials.appPassword()
        val authString = "${credentials.user}:${String(password)}"
        password.fill('\u0000')
        val encoded = Base64.getEncoder().encodeToString(authString.toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }
}
