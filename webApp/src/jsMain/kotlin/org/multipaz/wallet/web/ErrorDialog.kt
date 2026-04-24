package org.multipaz.wallet.web

import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.p
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import web.cssom.ClassName

external interface ErrorDialogProps : Props {
    var title: String
    var message: String
    var onDismiss: () -> Unit
}

val ErrorDialog = FC<ErrorDialogProps> { props ->
    div {
        className = ClassName("fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm animate-in fade-in duration-200")
        onClick = { props.onDismiss() }

        div {
            className = ClassName("bg-white dark:bg-slate-800 w-full max-w-md rounded-[2.5rem] shadow-2xl border border-slate-200 dark:border-slate-700 p-8 space-y-6 animate-in zoom-in-95 duration-200")
            onClick = { it.stopPropagation() }

            div {
                className = ClassName("space-y-2 text-center")
                div {
                    className = ClassName("mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 mb-4")
                    ErrorIcon { }
                }
                h2 {
                    className = ClassName("text-2xl font-bold text-slate-900 dark:text-white")
                    +props.title
                }
                p {
                    className = ClassName("text-sm text-slate-500 dark:text-slate-400 whitespace-pre-wrap")
                    +props.message
                }
            }

            div {
                className = ClassName("pt-2")
                button {
                    className = ClassName("w-full py-4 bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white font-bold rounded-2xl shadow-lg shadow-blue-500/20 transition-all active:scale-[0.98] focus:outline-none")
                    onClick = { props.onDismiss() }
                    +"Dismiss"
                }
            }
        }
    }
}

val ErrorIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-6 w-6"
        d.fill = "none"
        d.viewBox = "0 0 24 24"
        d.stroke = "currentColor"
        path {
            val pd: dynamic = this
            pd.strokeLinecap = "round"
            pd.strokeLinejoin = "round"
            pd.strokeWidth = 2.0
            pd.d = "M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
        }
    }
}
