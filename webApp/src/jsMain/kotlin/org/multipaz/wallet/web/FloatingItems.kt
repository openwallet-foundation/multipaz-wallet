package org.multipaz.wallet.web

import kotlinx.io.bytestring.ByteString
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import react.FC
import react.Props
import react.PropsWithChildren
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import web.cssom.ClassName

// --- Container ---

val FloatingItemList = FC<PropsWithChildren> { props ->
    div {
        className = ClassName("bg-white rounded-3xl shadow-xl border border-slate-100 overflow-hidden divide-y divide-slate-50")
        +props.children
    }
}

// --- Items ---

external interface FloatingItemProps : Props {
    var onClick: (() -> Unit)?
    var icon: FC<Props>?
    var title: String
    var subtitle: String?
    var titleClassName: String?
    var trailingIcon: FC<Props>?
}

/**
 * A generic floating list item that can have an icon, title, and subtitle.
 */
val FloatingItem = FC<FloatingItemProps> { props ->
    button {
        className = ClassName("w-full flex items-center px-6 py-4 text-left hover:bg-slate-50 active:bg-slate-100 transition-colors group focus:outline-none")
        onClick = { props.onClick?.invoke() }
        
        // Leading Icon
        if (props.icon != null) {
            div {
                className = ClassName("flex-shrink-0 w-10 h-10 flex items-center justify-center rounded-full bg-slate-50 group-hover:bg-white transition-colors mr-4")
                props.icon?.invoke { }
            }
        }
        
        // Text Content
        div {
            className = ClassName("flex-grow")
            div {
                className = ClassName("text-sm font-semibold ${props.titleClassName ?: "text-slate-900"}")
                +props.title
            }
            props.subtitle?.let {
                div {
                    className = ClassName("text-xs text-slate-500 mt-0.5")
                    +it
                }
            }
        }
        
        // Trailing Icon
        div {
            className = ClassName("flex-shrink-0 ml-4")
            if (props.trailingIcon != null) {
                props.trailingIcon?.invoke { }
            } else if (props.onClick != null) {
                SmallChevronRightIcon()
            }
        }
    }
}

val FloatingItemText = FloatingItem

external interface FloatingItemCardProps : Props {
    var onClick: (() -> Unit)?
    var cardArt: ByteString?
    var title: String
    var subtitle: String?
}

/**
 * Specifically for identity passes, using a credit card aspect ratio (1.586) for the art.
 */
val FloatingItemCard = FC<FloatingItemCardProps> { props ->
    val cardArtUrl = useImageUri(props.cardArt)
    
    button {
        className = ClassName("w-full flex items-center px-6 py-5 text-left hover:bg-slate-50 active:bg-slate-100 transition-colors group focus:outline-none")
        onClick = { props.onClick?.invoke() }
        
        // Leading Card Art
        div {
            className = ClassName("flex-shrink-0 w-20 aspect-[1.586/1] bg-slate-100 rounded-md border border-slate-200 overflow-hidden mr-6 shadow-sm group-hover:shadow-md transition-shadow flex items-center justify-center")
            if (cardArtUrl != null) {
                img {
                    src = cardArtUrl
                    className = ClassName("w-full h-full object-cover")
                }
            } else {
                div {
                    className = ClassName("text-slate-300 w-8 h-8")
                    TicketIcon()
                }
            }
        }
        
        // Text Content
        div {
            className = ClassName("flex-grow")
            div {
                className = ClassName("text-base font-bold text-slate-900")
                +props.title
            }
            props.subtitle?.let {
                div {
                    className = ClassName("text-sm text-slate-500 mt-0.5")
                    +it
                }
            }
        }
        
        // Trailing Action
        div {
            className = ClassName("flex-shrink-0 ml-4")
            SmallChevronRightIcon()
        }
    }
}

external interface FloatingItemPictureProps : Props {
    var title: String
    var picture: ByteString?
}

/**
 * Specifically for displaying a picture claim (portrait, signature, etc.).
 * Handles arbitrary aspect ratios gracefully.
 */
val FloatingItemPicture = FC<FloatingItemPictureProps> { props ->
    val imageUrl = useImageUri(props.picture)
    
    div {
        className = ClassName("w-full flex flex-col px-6 py-5 space-y-3")
        
        div {
            className = ClassName("text-sm font-semibold text-slate-900")
            +props.title
        }
        
        if (imageUrl != null) {
            img {
                src = imageUrl
                // max-h-64 prevents images from being too tall while allowing natural width/aspect ratio
                className = ClassName("max-h-64 w-auto rounded-2xl border border-slate-100 shadow-sm object-contain bg-slate-50")
            }
        } else {
            div {
                className = ClassName("w-32 h-40 bg-slate-100 rounded-2xl border border-slate-200 flex items-center justify-center")
                div {
                    className = ClassName("text-slate-300 w-12 h-12")
                    UserIcon()
                }
            }
        }
    }
}

external interface FloatingItemHeadingAndContentProps : PropsWithChildren {
    var heading: String
}

val FloatingItemHeadingAndContent = FC<FloatingItemHeadingAndContentProps> { props ->
    div {
        className = ClassName("w-full flex flex-col px-6 py-4 space-y-1")
        div {
            className = ClassName("text-xs font-semibold text-slate-500 uppercase tracking-wider")
            +props.heading
        }
        div {
            className = ClassName("text-base text-slate-900 break-all")
            +props.children
        }
    }
}

external interface FloatingItemHeadingAndTextProps : Props {
    var heading: String
    var text: String
}

val FloatingItemHeadingAndText = FC<FloatingItemHeadingAndTextProps> { props ->
    FloatingItemHeadingAndContent {
        heading = props.heading
        +props.text
    }
}

external interface FloatingItemHeadingAndDateProps : Props {
    var heading: String
    var date: Instant?
}

val FloatingItemHeadingAndDate = FC<FloatingItemHeadingAndDateProps> { props ->
    FloatingItemHeadingAndContent {
        heading = props.heading
        val dateText = if (props.date != null) {
            val dateTime = props.date!!.toLocalDateTime(TimeZone.currentSystemDefault())
            "${dateTime.year}-${dateTime.month.toString().padStart(2, '0')}-${dateTime.day.toString().padStart(2, '0')} " +
                    "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}:${dateTime.second.toString().padStart(2, '0')}"
        } else {
            "N/A"
        }
        +dateText
    }
}

// --- Common Icons ---

val SmallChevronRightIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-5 w-5 text-slate-300 group-hover:text-blue-500 transition-colors"
        d.fill = "none"
        d.viewBox = "0 0 24 24"
        d.stroke = "currentColor"
        path {
            val pd: dynamic = this
            pd.strokeLinecap = "round"
            pd.strokeLinejoin = "round"
            pd.strokeWidth = 2.0
            pd.d = "M9 5l7 7-7 7"
        }
    }
}

val SettingsIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-5 w-5"
        d.fill = "none"
        d.viewBox = "0 0 24 24"
        d.stroke = "currentColor"
        path {
            val pd: dynamic = this
            pd.strokeLinecap = "round"
            pd.strokeLinejoin = "round"
            pd.strokeWidth = 2.0
            pd.d = "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
        }
        react.dom.svg.ReactSVG.circle {
            val cd: dynamic = this
            cd.cx = 12
            cd.cy = 12
            cd.r = 3
            cd.strokeWidth = 2.0
        }
    }
}

val LogoutIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-5 w-5"
        d.fill = "none"
        d.viewBox = "0 0 24 24"
        d.stroke = "currentColor"
        path {
            val pd: dynamic = this
            pd.strokeLinecap = "round"
            pd.strokeLinejoin = "round"
            pd.strokeWidth = 2.0
            pd.d = "M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
        }
    }
}

val TicketIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-full w-full"
        d.fill = "none"
        d.viewBox = "0 0 24 24"
        d.stroke = "currentColor"
        path {
            val pd: dynamic = this
            pd.strokeLinecap = "round"
            pd.strokeLinejoin = "round"
            pd.strokeWidth = 1.5
            pd.d = "M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z"
        }
    }
}

val UserIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-full w-full"
        d.fill = "none"
        d.viewBox = "0 0 24 24"
        d.stroke = "currentColor"
        path {
            val pd: dynamic = this
            pd.strokeLinecap = "round"
            pd.strokeLinejoin = "round"
            pd.strokeWidth = 2.0
            pd.d = "M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
        }
    }
}
