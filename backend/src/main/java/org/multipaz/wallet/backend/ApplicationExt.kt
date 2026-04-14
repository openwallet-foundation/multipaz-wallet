package org.multipaz.wallet.backend

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
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
import org.multipaz.util.Platform
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
        get ("/") {
            val configuration = BackendEnvironment.getInterface(Configuration::class)!!
            val googleSiteVerificationToken = configuration.getValue("googleSiteVerificationToken") ?: ""
            call.respondText(
                contentType = ContentType.Text.Html,
                text = """
                    <html lang="en">
                    <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <meta name="google-site-verification" content="$googleSiteVerificationToken" />
                        <title>${BuildConfig.APP_NAME}</title>
                    </head>
                    <body>
                      <i>${BuildConfig.APP_NAME}</i> version ${BuildConfig.VERSION} is running.
                      <p>
                      Powered by Multipaz SDK version ${Platform.version}.
                      <p>
                      <a href="privacy-policy.html">Privacy policy</a>
                      <br>
                      <a href="terms-of-service.html">Terms of service</a>
                    </body>
                """.trimIndent()
            )
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