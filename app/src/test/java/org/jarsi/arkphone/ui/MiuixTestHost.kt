package org.jarsi.arkphone.ui

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The app wraps every screen in a Miuix theme and Scaffold; popups such as
 *  the dropdown need that Scaffold, so tests mount their content the same way. */
@Composable
fun MiuixTestHost(content: @Composable () -> Unit) {
    MiuixTheme {
        Scaffold { content() }
    }
}
