package org.multipaz.wallet.web

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.util.Logger
import org.multipaz.wallet.shared.BuildConfig
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.ul
import react.useEffectOnce
import react.useState
import web.cssom.ClassName

private const val TAG = "DocViewerApp"

external interface DocViewerProps : Props {
    var docId: String?
}

enum class DocStatus {
    LOADING,
    SUCCESS,
    ERROR
}

data class DocInfo(
    val id: String,
    val title: String,
    val content: String
)

val DocViewerApp = FC<DocViewerProps> { props ->
    val (status, setStatus) = useState(DocStatus.LOADING)
    val (errorMessage, setErrorMessage) = useState<String?>(null)
    val (docInfo, setDocInfo) = useState<DocInfo?>(null)

    val currentDocId = props.docId ?: run {
        val path = window.location.pathname.removePrefix("/web")
        when {
            path.startsWith("/terms") -> "terms"
            path.startsWith("/google-privacy") -> "google-privacy"
            path.startsWith("/google-terms") -> "google-terms"
            else -> "privacy"
        }
    }

    useEffectOnce {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val response = window.fetch("/api/docs/$currentDocId").await()
                if (!response.ok) {
                    throw Exception("Failed to load document (HTTP ${response.status})")
                }
                val text = response.text().await()
                val rootObj = Json.parseToJsonElement(text).jsonObject
                val id = rootObj["id"]?.jsonPrimitive?.content ?: currentDocId
                val rawContent = rootObj["content"]?.jsonPrimitive?.content ?: ""
                val content = rawContent
                    .replace("\${BuildConfig.APP_NAME}", BuildConfig.APP_NAME)
                    .replace("\${APP_NAME}", BuildConfig.APP_NAME)

                val title = content.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
                    ?: when (id) {
                        "terms" -> "Terms of Service for ${BuildConfig.APP_NAME}"
                        "google-privacy" -> "Sign in with Google & Data Use Policy"
                        "google-terms" -> "Sign in with Google Terms of Service"
                        else -> "Privacy Policy for ${BuildConfig.APP_NAME}"
                    }

                window.document.title = "$title - ${BuildConfig.APP_NAME}"
                setDocInfo(DocInfo(id, title, content))
                setStatus(DocStatus.SUCCESS)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to fetch document $currentDocId", e)
                setErrorMessage(e.message ?: "Failed to fetch document")
                setStatus(DocStatus.ERROR)
            }
        }
    }

    div {
        className = ClassName("flex flex-col items-center justify-center min-h-screen bg-slate-900 text-white px-4 py-12 relative overflow-hidden")

        // Animated gradient background accents
        div {
            className = ClassName("absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-blue-600/20 rounded-full blur-3xl -z-10 animate-pulse")
        }
        div {
            className = ClassName("absolute bottom-1/4 left-1/3 w-80 h-80 bg-indigo-600/15 rounded-full blur-3xl -z-10")
        }

        div {
            className = ClassName("max-w-3xl w-full bg-slate-950/80 backdrop-blur-xl border border-slate-800/80 rounded-3xl p-8 shadow-2xl space-y-8 flex flex-col items-center")

            // Header Section
            div {
                className = ClassName("flex flex-col items-center text-center space-y-3 w-full")
                div {
                    className = ClassName("relative mb-2")
                    div {
                        className = ClassName("absolute -inset-1 bg-gradient-to-r from-blue-600 to-indigo-600 rounded-full blur opacity-30")
                    }
                    img {
                        src = "https://apps.multipaz.org/multipaz-logo-400x400.png"
                        className = ClassName("relative h-24 w-24 rounded-full border-2 border-slate-800")
                    }
                }
                h1 {
                    className = ClassName("text-3xl font-extrabold text-slate-100 tracking-tight")
                    +(docInfo?.title ?: "Document Viewer")
                }
                p {
                    className = ClassName("text-sm text-slate-400 max-w-md leading-relaxed")
                    +"Legal disclosures, policies, and terms for ${BuildConfig.APP_NAME}."
                }

                // Nav Tabs
                div {
                    className = ClassName("flex flex-wrap justify-center gap-1.5 pt-2 bg-slate-900/80 p-1.5 rounded-2xl border border-slate-800")
                    renderDocTab("privacy", "Privacy Policy", currentDocId)
                    renderDocTab("terms", "Terms of Service", currentDocId)
                    renderDocTab("google-privacy", "Google Sign-In Policy", currentDocId)
                    renderDocTab("google-terms", "Google Sign-In Terms", currentDocId)
                }
            }

            when (status) {
                DocStatus.LOADING -> {
                    div {
                        className = ClassName("flex flex-col items-center space-y-4 py-12")
                        div {
                            className = ClassName("w-12 h-12 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin")
                        }
                        p {
                            className = ClassName("text-sm text-slate-400")
                            +"Loading document..."
                        }
                    }
                }
                DocStatus.ERROR -> {
                    div {
                        className = ClassName("w-full bg-red-950/50 border border-red-800/50 rounded-2xl p-6 text-center space-y-3")
                        h2 {
                            className = ClassName("text-lg font-bold text-red-300")
                            +"Unable to load document"
                        }
                        p {
                            className = ClassName("text-xs text-red-400")
                            +(errorMessage ?: "An unexpected error occurred.")
                        }
                        button {
                            className = ClassName("px-4 py-2 bg-slate-800 hover:bg-slate-700 text-xs text-slate-200 rounded-xl transition-all cursor-pointer")
                            onClick = { window.location.reload() }
                            +"Retry"
                        }
                    }
                }
                DocStatus.SUCCESS -> {
                    val info = docInfo
                    if (info != null) {
                        div {
                            className = ClassName("w-full bg-slate-900/60 border border-slate-800/80 rounded-2xl p-6 text-left space-y-4 shadow-inner")
                            renderMarkdownContent(info.content)
                        }
                    }
                }
            }
        }
    }
}

private fun ChildrenBuilder.renderDocTab(id: String, label: String, currentId: String) {
    a {
        href = "/$id"
        className = ClassName(
            if (id == currentId)
                "px-3 py-1.5 text-xs font-semibold rounded-xl bg-indigo-600 text-white shadow-sm border border-indigo-500/40 transition-all"
            else
                "px-3 py-1.5 text-xs font-medium rounded-xl text-slate-400 hover:text-slate-200 transition-all"
        )
        +label
    }
}

private fun ChildrenBuilder.renderMarkdownContent(content: String) {
    val lines = content.lines()
    var inList = false
    var listItems = mutableListOf<String>()

    val flushList = {
        if (inList && listItems.isNotEmpty()) {
            ul {
                className = ClassName("list-disc list-inside space-y-1.5 text-sm text-slate-300 pl-2")
                listItems.forEach { item ->
                    li {
                        renderFormattedInlineText(item)
                    }
                }
            }
            listItems.clear()
            inList = false
        }
    }

    lines.forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("# ") -> {
                flushList()
                // Main title rendered in header, skip top H1 or render nicely
            }
            trimmed.startsWith("## ") -> {
                flushList()
                h2 {
                    className = ClassName("text-lg font-bold text-slate-100 pt-3 border-t border-slate-800/60")
                    renderFormattedInlineText(trimmed.removePrefix("## "))
                }
            }
            trimmed.startsWith("### ") -> {
                flushList()
                h3 {
                    className = ClassName("text-sm font-semibold text-indigo-300 pt-2")
                    renderFormattedInlineText(trimmed.removePrefix("### "))
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                inList = true
                val itemText = if (trimmed.startsWith("- ")) trimmed.removePrefix("- ") else trimmed.removePrefix("* ")
                listItems.add(itemText)
            }
            trimmed.isEmpty() -> {
                flushList()
            }
            else -> {
                flushList()
                p {
                    className = ClassName("text-sm text-slate-300 leading-relaxed")
                    renderFormattedInlineText(trimmed)
                }
            }
        }
    }
    flushList()
}

private fun ChildrenBuilder.renderFormattedInlineText(text: String) {
    // Regex matching inline elements:
    // Group 1 & 2: Link [label](url)
    // Group 3: Bold **text**
    // Group 4: Code `code`
    // Group 5: Italic *text*
    val tokenRegex = Regex("\\[([^\\]]+)\\]\\(([^\\)]+)\\)|\\*\\*([^\\*]+)\\*\\*|`([^`]+)`|\\*([^\\*]+)\\*")
    var lastIndex = 0

    tokenRegex.findAll(text).forEach { match ->
        val range = match.range
        if (range.first > lastIndex) {
            +text.substring(lastIndex, range.first)
        }

        val fullMatch = match.value
        when {
            fullMatch.startsWith("[") -> {
                val label = match.groupValues[1]
                val url = match.groupValues[2]
                a {
                    href = url
                    (this.asDynamic()).target = "_blank"
                    className = ClassName("text-indigo-400 hover:underline font-medium")
                    +label
                }
            }
            fullMatch.startsWith("**") -> {
                val boldText = match.groupValues[3]
                span {
                    className = ClassName("font-bold text-slate-100")
                    +boldText
                }
            }
            fullMatch.startsWith("`") -> {
                val codeText = match.groupValues[4]
                span {
                    className = ClassName("bg-slate-950 text-indigo-300 px-1.5 py-0.5 rounded border border-slate-800 font-mono text-xs")
                    +codeText
                }
            }
            fullMatch.startsWith("*") -> {
                val italicText = match.groupValues[5]
                span {
                    className = ClassName("italic text-slate-400")
                    +italicText
                }
            }
        }
        lastIndex = range.last + 1
    }

    if (lastIndex < text.length) {
        +text.substring(lastIndex)
    }
}
