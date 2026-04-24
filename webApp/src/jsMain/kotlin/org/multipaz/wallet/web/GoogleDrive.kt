package org.multipaz.wallet.web

import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.multipaz.util.Logger
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import kotlin.js.json
import kotlin.random.Random
import kotlin.js.Promise
import kotlinx.coroutines.await

private const val TAG = "GoogleDrive"

class GoogleDrive(private val accessToken: String) {
    private val fileName = "WalletServerEncryptionKey"

    suspend fun retrieveOrCreateEncryptionKey(resetEncryptionKey: Boolean): ByteArray {
        val existingFileId = findFile(fileName)

        if (!resetEncryptionKey && existingFileId != null) {
            Logger.i(TAG, "Found existing encryption key in Drive.")
            val keyBytes = downloadFile(existingFileId)
            if (keyBytes.size == 32) {
                return keyBytes
            }
            Logger.w(TAG, "Existing key was not 32 bytes (size: ${keyBytes.size}). Regenerating.")
        }

        Logger.i(TAG, "Creating new 32-byte encryption key.")
        val newKeyBytes = Random.nextBytes(32)

        if (existingFileId != null) {
            updateFile(existingFileId, newKeyBytes)
        } else {
            createFile(fileName, newKeyBytes)
        }

        return newKeyBytes
    }

    private suspend fun findFile(name: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$name'&fields=files(id, name)"
        val response = authenticatedFetch(url)
        val data = response.json().await().asDynamic()
        val files = data.files as Array<dynamic>
        return if (files.isNotEmpty()) files[0].id as String else null
    }

    private suspend fun downloadFile(fileId: String): ByteArray {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val response = authenticatedFetch(url)
        val buffer = response.arrayBuffer().await().asDynamic() as ArrayBuffer
        return Uint8Array(buffer).toByteArray()
    }

    private suspend fun createFile(name: String, content: ByteArray) {
        val metadata = json(
            "name" to name,
            "parents" to arrayOf("appDataFolder")
        )
        
        val boundary = "-------314159265358979323846"
        val delimiter = "\r\n--$boundary\r\n"
        val closeDelimiter = "\r\n--$boundary--"

        val body = StringBuilder()
            .append(delimiter)
            .append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            .append(JSON.stringify(metadata))
            .append(delimiter)
            .append("Content-Type: application/octet-stream\r\n\r\n")
            .toString()

        // We need to combine string and binary data. In JS this is often done with Blob.
        val blob = window.asDynamic().Blob(
            arrayOf(body, Uint8Array(content.toTypedArray()), closeDelimiter),
            json("type" to "multipart/related; boundary=$boundary")
        )

        authenticatedFetch("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", 
            js("{}").unsafeCast<RequestInit>().apply {
                method = "POST"
                this.body = blob
            }
        )
    }

    private suspend fun updateFile(fileId: String, content: ByteArray) {
        authenticatedFetch("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media",
            js("{}").unsafeCast<RequestInit>().apply {
                method = "PATCH"
                this.body = Uint8Array(content.toTypedArray())
            }
        )
    }

    private suspend fun authenticatedFetch(url: String, init: RequestInit = js("{}").unsafeCast<RequestInit>()): Response {
        val dInit: dynamic = init
        if (dInit.headers == null || dInit.headers == undefined) {
            dInit.headers = js("{}")
        }
        dInit.headers["Authorization"] = "Bearer $accessToken"
        
        val response = window.fetch(url, init).await()
        if (!response.ok) {
            throw Exception("Drive API error: ${response.status} ${response.statusText}")
        }
        return response
    }

    private fun Uint8Array.toByteArray(): ByteArray {
        val array = ByteArray(length)
        for (i in 0 until length) {
            array[i] = get(i)
        }
        return array
    }
}
