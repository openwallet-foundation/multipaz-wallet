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
import react.dom.svg.ReactSVG.circle
import web.cssom.ClassName

// --- Container ---

val FloatingItemList = FC<PropsWithChildren> { props ->
    div {
        className = ClassName("bg-white dark:bg-slate-800 rounded-3xl shadow-balanced dark:shadow-balanced-dark border border-slate-100 dark:border-slate-700 overflow-hidden divide-y divide-slate-50 dark:divide-slate-700 transition-colors duration-300")
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
        className = ClassName("w-full flex items-center px-6 py-4 text-left hover:bg-slate-50 dark:hover:bg-slate-700 active:bg-slate-100 dark:active:bg-slate-600 transition-colors group focus:outline-none")
        onClick = { props.onClick?.invoke() }
        
        // Leading Icon
        if (props.icon != null) {
            div {
                className = ClassName("flex-shrink-0 w-10 h-10 flex items-center justify-center rounded-full bg-slate-50 dark:bg-slate-900 group-hover:bg-white dark:group-hover:bg-slate-800 transition-colors mr-4")
                props.icon?.invoke { }
            }
        }
        
        // Text Content
        div {
            className = ClassName("flex-grow")
            div {
                className = ClassName("text-sm font-semibold ${props.titleClassName ?: "text-slate-900 dark:text-slate-100"}")
                +props.title
            }
            props.subtitle?.let {
                div {
                    className = ClassName("text-xs text-slate-500 dark:text-slate-400 mt-0.5")
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
        className = ClassName("w-full flex items-center px-6 py-5 text-left hover:bg-slate-50 dark:hover:bg-slate-700 active:bg-slate-100 dark:active:bg-slate-600 transition-colors duration-300 group focus:outline-none")
        onClick = { props.onClick?.invoke() }
        
        // Leading Card Art
        div {
            className = ClassName("flex-shrink-0 w-20 aspect-[1.586/1] bg-slate-100 dark:bg-slate-900 rounded-md border border-slate-200 dark:border-slate-700 overflow-hidden mr-6 shadow-sm group-hover:shadow-md transition-shadow flex items-center justify-center")
            if (cardArtUrl != null) {
                img {
                    src = cardArtUrl
                    className = ClassName("w-full h-full object-cover")
                }
            } else {
                div {
                    className = ClassName("text-slate-300 dark:text-slate-600 w-8 h-8")
                    TicketIcon()
                }
            }
        }
        
        // Text Content
        div {
            className = ClassName("flex-grow")
            div {
                className = ClassName("text-base font-bold text-slate-900 dark:text-white")
                +props.title
            }
            props.subtitle?.let {
                div {
                    className = ClassName("text-sm text-slate-500 dark:text-slate-400 mt-0.5")
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
        className = ClassName("w-full flex flex-col px-6 py-5 space-y-3 transition-colors duration-300")
        
        div {
            className = ClassName("text-sm font-semibold text-slate-900 dark:text-white")
            +props.title
        }
        
        if (imageUrl != null) {
            img {
                src = imageUrl
                // max-h-64 prevents images from being too tall while allowing natural width/aspect ratio
                className = ClassName("max-h-64 w-auto rounded-2xl border border-slate-100 dark:border-slate-700 shadow-sm object-contain bg-slate-50 dark:bg-slate-900")
            }
        } else {
            div {
                className = ClassName("w-32 h-40 bg-slate-100 dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-700 flex items-center justify-center")
                div {
                    className = ClassName("text-slate-300 dark:text-slate-600 w-12 h-12")
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
        className = ClassName("w-full flex flex-col px-6 py-4 space-y-1 transition-colors duration-300")
        div {
            className = ClassName("text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider")
            +props.heading
        }
        div {
            className = ClassName("text-base text-slate-900 dark:text-slate-100 break-all")
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

val PlusIcon = FC<Props> {
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
            pd.d = "M12 4v16m8-8H4"
        }
    }
}

val FileUploadIcon = FC<Props> {
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
            pd.d = "M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"
        }
    }
}

val AccountBalanceIcon = FC<Props> {
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
            pd.d = "M3 10h18M3 14h18m-9-4v4m-3-4v4m6-4v4M4 6h16a1 1 0 011 1v2H3V7a1 1 0 011-1zm-1 12h18v1a1 1 0 01-1 1H4a1 1 0 01-1-1v-1z"
        }
    }
}

val BackIcon = FC<Props> {
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
            pd.d = "M15 19l-7-7 7-7"
        }
    }
}

val TrashIcon = FC<Props> {
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
            pd.strokeWidth = 1.5
            pd.d = "m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0"
        }
    }
}

val SmallChevronRightIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-5 w-5 text-slate-300 dark:text-slate-600 group-hover:text-blue-500 dark:group-hover:text-blue-400 transition-colors"
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
        circle {
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
