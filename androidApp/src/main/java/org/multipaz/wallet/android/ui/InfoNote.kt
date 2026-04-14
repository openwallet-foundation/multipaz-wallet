package org.multipaz.wallet.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.multipaz.compose.text.fromMarkdown

@Composable
fun InfoNote(
    markdownString: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null)
        Text(
            text = AnnotatedString.fromMarkdown(markdownString = markdownString),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}