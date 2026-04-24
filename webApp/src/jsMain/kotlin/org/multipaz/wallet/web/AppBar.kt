package org.multipaz.wallet.web

import react.FC
import react.Props
import react.PropsWithChildren
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface AppBarProps : Props {
    var title: String
    var leftContent: FC<Props>?
    var actions: FC<Props>?
    var settingsModel: SettingsModel
}

val AppBar = FC<AppBarProps> { props ->
    val darkMode = useFlow(props.settingsModel.darkMode)

    header {
        className = ClassName("sticky top-0 bg-white dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700 z-50 h-16 transition-colors duration-300")
        div {
            className = ClassName("max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-full")
            div {
                className = ClassName("grid grid-cols-3 h-full items-center")

                // Left Slot
                div {
                    className = ClassName("flex justify-start items-center h-full")
                    props.leftContent?.invoke { }
                }

                // Center Slot: Title
                div {
                    className = ClassName("flex justify-center items-center h-full")
                    span {
                        className = ClassName("text-xl font-bold text-slate-900 dark:text-white tracking-tight truncate")
                        +props.title
                    }
                }

                // Right Slot: Actions
                div {
                    className = ClassName("flex justify-end items-center space-x-2 h-full")
                    
                    // Theme Toggle (Always present)
                    button {
                        className = ClassName("p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 rounded-full transition-all focus:outline-none")
                        title = "Appearance: ${darkMode.label}"
                        onClick = {
                            val nextMode = when (darkMode) {
                                DarkMode.AUTO -> DarkMode.LIGHT
                                DarkMode.LIGHT -> DarkMode.DARK
                                DarkMode.DARK -> DarkMode.AUTO
                            }
                            props.settingsModel.darkMode.value = nextMode
                        }
                        when (darkMode) {
                            DarkMode.AUTO -> SparklesIcon { }
                            DarkMode.LIGHT -> SunIcon { }
                            DarkMode.DARK -> MoonIcon { }
                        }
                    }

                    // Other Actions
                    props.actions?.invoke { }
                }
            }
        }
    }
}
