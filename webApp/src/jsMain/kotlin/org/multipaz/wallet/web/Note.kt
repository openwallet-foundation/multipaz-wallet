package org.multipaz.wallet.web

import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.p
import web.cssom.ClassName

external interface NoteProps : PropsWithChildren {
    var text: String?
}

val Note = FC<NoteProps> { props ->
    p {
        className = ClassName("text-sm text-slate-600 dark:text-slate-400 px-1 leading-relaxed")
        if (props.text != null) {
            +props.text!!
        }
        props.children?.let { child ->
            +child
        }
    }
}
