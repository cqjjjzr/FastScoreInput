package com.github.charlie.scoreinput

import java.awt.Component
import java.util.prefs.Preferences
import javax.swing.JOptionPane

inline fun tryCatch(msg: String, block: () -> Unit) {
    try {
        block()
    } catch (ex: Exception) {
        showErrorDialog("$msg\n${ex.javaClass.name}: ${ex.message}")
    }
}

inline fun <T> T.tryCatch(msg: String, block: T.() -> Unit) {
    try {
        block()
    } catch (ex: Exception) {
        showErrorDialog("$msg\n${ex.javaClass.name}: ${ex.message}")
    }
}

inline fun runIgnoreException(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) { /* ignored*/ }
}

fun showErrorDialog(message: String, caption: String = "错误", parent: Component? = null) = JOptionPane.showMessageDialog(parent, message, caption, JOptionPane.ERROR_MESSAGE)

val preference by lazy { Preferences.userNodeForPackage(MainFrame::class.java)!! }