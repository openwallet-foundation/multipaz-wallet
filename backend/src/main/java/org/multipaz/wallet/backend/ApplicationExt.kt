package org.multipaz.wallet.backend

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.multipaz.rpc.handler.HttpHandler
import org.multipaz.rpc.handler.RpcDispatcherLocal
import org.multipaz.rpc.handler.RpcExceptionMap
import org.multipaz.rpc.handler.RpcPoll
import org.multipaz.rpc.handler.SimpleCipher
import org.multipaz.rpc.server.ClientCheckImpl
import org.multipaz.rpc.server.ClientRegistrationImpl
import org.multipaz.rpc.server.register
import org.multipaz.server.common.ServerEnvironment
import org.multipaz.server.request.certificateAuthority
import org.multipaz.server.request.push
import org.multipaz.server.request.rpc
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.server.enrollment.ServerIdentity
import org.multipaz.server.enrollment.getServerIdentity
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.WalletBackendException
import org.multipaz.wallet.shared.register
import java.util.Locale

private const val TAG = "ApplicationExt"

/**
 * Defines server entry points for HTTP GET and POST.
 */
fun Application.configureRouting(serverEnvironment: Deferred<ServerEnvironment>) {
    val httpHandler = initAndCreateHttpHandler(serverEnvironment)
    routing {
        push(serverEnvironment)
        certificateAuthority()
        get("/web") {
            call.respondRedirect("/web/")
        }
        staticResources("/web", "static/web", index = "index.html")
        get("/webApp.js") {
            val content = this::class.java.classLoader.getResource("static/web/webApp.js")?.readBytes()
            if (content != null) {
                call.respondBytes(content, ContentType.Application.JavaScript)
            } else {
                call.respondText("Not found", status = io.ktor.http.HttpStatusCode.NotFound)
            }
        }
        get("/verify") {
            val content = this::class.java.classLoader.getResource("static/web/verify.html")?.readText()
            if (content != null) {
                call.respondText(content, ContentType.Text.Html)
            } else {
                call.respondText("Verification page not found", status = io.ktor.http.HttpStatusCode.NotFound)
            }
        }
        get("/web/verify") {
            val content = this::class.java.classLoader.getResource("static/web/verify.html")?.readText()
            if (content != null) {
                call.respondText(content, ContentType.Text.Html)
            } else {
                call.respondText("Verification page not found", status = io.ktor.http.HttpStatusCode.NotFound)
            }
        }
        get("/keys") {
            val content = this::class.java.classLoader.getResource("static/web/keys.html")?.readText()
            if (content != null) {
                call.respondText(content, ContentType.Text.Html)
            } else {
                call.respondText("Keys page not found", status = io.ktor.http.HttpStatusCode.NotFound)
            }
        }
        get("/web/keys") {
            val content = this::class.java.classLoader.getResource("static/web/keys.html")?.readText()
            if (content != null) {
                call.respondText(content, ContentType.Text.Html)
            } else {
                call.respondText("Keys page not found", status = io.ktor.http.HttpStatusCode.NotFound)
            }
        }
        get("/api/keys") {
            val env = serverEnvironment.await()
            val (walletKey, keyKey, readerRootKey) = withContext(env) {
                Triple(
                    getServerIdentity(ServerIdentity.WALLET_ATTESTATION),
                    getServerIdentity(ServerIdentity.KEY_ATTESTATION),
                    getServerIdentity(ServerIdentity.READER_ROOT)
                )
            }
            val prettyJson = Json { prettyPrint = true }
            val walletJwk = walletKey.publicKey.toJwk()
            val keyJwk = keyKey.publicKey.toJwk()
            val readerRootJwk = readerRootKey.publicKey.toJwk()

            val responseJson = buildJsonObject {
                putJsonObject("walletAttestation") {
                    put("name", "Wallet Attestation Key")
                    put("publicKeyPem", walletKey.publicKey.toPem())
                    put("publicKeyJwk", walletJwk)
                    put("publicKeyJwkString", prettyJson.encodeToString(JsonObject.serializer(), walletJwk))
                    putJsonArray("certificates") {
                        val certs = (walletKey as? AsymmetricKey.X509Certified)?.certChain?.certificates
                        certs?.forEach { add(it.toPem()) }
                    }
                }
                putJsonObject("keyAttestation") {
                    put("name", "Key Attestation Key")
                    put("publicKeyPem", keyKey.publicKey.toPem())
                    put("publicKeyJwk", keyJwk)
                    put("publicKeyJwkString", prettyJson.encodeToString(JsonObject.serializer(), keyJwk))
                    putJsonArray("certificates") {
                        val certs = (keyKey as? AsymmetricKey.X509Certified)?.certChain?.certificates
                        certs?.forEach { add(it.toPem()) }
                    }
                }
                putJsonObject("readerRoot") {
                    put("name", "Reader Root Key")
                    put("publicKeyPem", readerRootKey.publicKey.toPem())
                    put("publicKeyJwk", readerRootJwk)
                    put("publicKeyJwkString", prettyJson.encodeToString(JsonObject.serializer(), readerRootJwk))
                    putJsonArray("certificates") {
                        val certs = (readerRootKey as? AsymmetricKey.X509Certified)?.certChain?.certificates
                        certs?.forEach { add(it.toPem()) }
                    }
                }
            }
            call.respondText(responseJson.toString(), ContentType.Application.Json)
        }
        get("/") {
            call.respondRedirect("/web/")
        }
        get ("/privacy-policy.html") {
            call.respondText(
                contentType = ContentType.Text.Html,
                text = """
                    <html lang="en">
                    <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <title>${BuildConfig.APP_NAME}</title>
                    </head>
                    <body>
                      Placeholder for privacy policy for <i>${BuildConfig.APP_NAME}</i>.
                      <p>
                      To be filled out.
                    </body>
                """.trimIndent()
            )
        }
        get ("/terms-of-service.html") {
            call.respondText(
                contentType = ContentType.Text.Html,
                text = """
                    <html lang="en">
                    <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <title>${BuildConfig.APP_NAME}</title>
                    </head>
                    <body>
                      Placeholder for terms of service for <i>${BuildConfig.APP_NAME}</i>.
                      <p>
                      To be filled out.
                    </body>
                """.trimIndent()
            )
        }
        get("/.well-known/assetlinks.json") {
            call.respondText(
                contentType = ContentType.Application.Json,
                text = generateAssetLinksJson().toString()
            )
        }
        get("/.well-known/apple-app-site-association") {
            call.respondText(
                contentType = ContentType.Application.Json,
                text = generateAppleAppSiteAssociationJson().toString()
            )
        }
        rpc("/rpc", httpHandler)
    }
}

private fun initAndCreateHttpHandler(
    environment: Deferred<ServerEnvironment>
): Deferred<HttpHandler> {
    return CoroutineScope(Dispatchers.Default).async {
        val env = environment.await()
        withContext(env) {
            OpenID4VCIBackendImpl.init()
        }
        val exceptionMap = buildExceptionMap()
        val dispatcherBuilder = buildDispatcher()
        val rpcPoll = env.getInterface(RpcPoll::class)!!
        val localDispatcher = dispatcherBuilder.build(
            env,
            env.getInterface(SimpleCipher::class)!!,
            exceptionMap
        )
        HttpHandler(localDispatcher, rpcPoll)
    }
}

private fun buildExceptionMap(): RpcExceptionMap {
    val exceptionMapBuilder = RpcExceptionMap.Builder()
    WalletBackendException.register(exceptionMapBuilder)
    return exceptionMapBuilder.build()
}

private fun buildDispatcher(): RpcDispatcherLocal.Builder {
    val dispatcherBuilder = RpcDispatcherLocal.Builder()
    ClientRegistrationImpl.register(dispatcherBuilder)
    ClientCheckImpl.register(dispatcherBuilder)
    WalletBackendImpl.register(dispatcherBuilder)
    OpenID4VCIBackendImpl.register(dispatcherBuilder)
    return dispatcherBuilder
}

private suspend fun generateAssetLinksJson(): JsonElement =
    buildJsonArray {
        val clientRequirements = ClientRegistrationImpl.getClientRequirements()
        // We only support assetlink generation for apps with a single signature
        for (digest in clientRequirements.androidAppSignatureCertificateDigests) {
            val digestString = digestToString(digest)
            for (packageName in clientRequirements.androidAppPackageNames) {
                addJsonObject {
                    putJsonArray("relation") {
                        add("delegate_permission/common.handle_all_urls")
                    }
                    putJsonObject("target") {
                        put("namespace", "android_app")
                        put("package_name", packageName)
                        putJsonArray("sha256_cert_fingerprints") {
                            add(digestString)
                        }
                    }
                }
            }
        }
    }

private fun digestToString(digest: ByteString): String =
    digest.toByteArray().joinToString(":") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }.uppercase(Locale.ROOT)

private suspend fun generateAppleAppSiteAssociationJson(): JsonElement {
    val clientRequirements = ClientRegistrationImpl.getClientRequirements()
    val appIds = buildJsonArray {
        clientRequirements.iosAppIdentifiers.forEach { add(it) }
    }
    return buildJsonObject {
        putJsonObject("applinks") {
            putJsonArray("details") {
                addJsonObject {
                    put("appIDs", appIds)
                    putJsonArray("components") {
                        addJsonObject {
                            put("/", "/redirect/")
                        }
                        addJsonObject {
                            put("/", "/landing/")  // legacy name
                        }
                    }
                }
            }
        }
        putJsonObject("webcredentials") {
            put("appIDs", appIds)
        }
    }
}