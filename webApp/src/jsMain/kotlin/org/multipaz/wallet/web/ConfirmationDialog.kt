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

external interface ConfirmationDialogProps : Props {
    var title: String
    var message: String
    var confirmButtonText: String
    var onConfirm: () -> Unit
    var onCancel: () -> Unit
}

val ConfirmationDialog = FC<ConfirmationDialogProps> { props ->
    div {
        className = ClassName("fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm animate-in fade-in duration-200")
        onClick = { props.onCancel() }

        div {
            className = ClassName("bg-white dark:bg-slate-800 w-full max-w-md rounded-[2.5rem] shadow-2xl border border-slate-200 dark:border-slate-700 p-8 space-y-6 animate-in zoom-in-95 duration-200")
            onClick = { it.stopPropagation() }

            div {
                className = ClassName("space-y-2 text-center")
                div {
                    className = ClassName("mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 mb-4")
                    TrashIcon { }
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
                className = ClassName("grid grid-cols-2 gap-4 pt-2")
                button {
                    className = ClassName("py-4 bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600 text-slate-900 dark:text-white font-bold rounded-2xl transition-all active:scale-[0.98] focus:outline-none")
                    onClick = { props.onCancel() }
                    +"Cancel"
                }
                button {
                    className = ClassName("py-4 bg-red-600 hover:bg-red-500 active:bg-red-700 text-white font-bold rounded-2xl shadow-lg shadow-red-500/20 transition-all active:scale-[0.98] focus:outline-none")
                    onClick = { props.onConfirm() }
                    +props.confirmButtonText
                }
            }
        }
    }
}
