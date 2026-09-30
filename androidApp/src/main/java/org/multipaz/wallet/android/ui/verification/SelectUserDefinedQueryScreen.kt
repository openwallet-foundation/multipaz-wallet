package org.multipaz.wallet.android.ui.verification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.multipaz.compose.items.SegmentedItemContainer
import org.multipaz.compose.items.SegmentedItemList
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.verification.UserDefinedQuery


private class NamespaceEntryState(
    name: String = "",
    elements: List<String> = listOf("")
) {
    var name by mutableStateOf(name)
    val elements = mutableStateListOf<String>().apply { addAll(elements) }
}

/**
 * The result of checking the input of [SelectUserDefinedQueryScreen].
 */
internal sealed class UserDefinedQueryInput {
    /** The input makes up [query]. */
    data class Valid(val query: UserDefinedQuery) : UserDefinedQueryInput()

    /** The input cannot be saved, for the reasons given. */
    data class Invalid(
        val missingDocType: Boolean,
        val missingDataElements: Boolean
    ) : UserDefinedQueryInput()
}

/**
 * Builds a [UserDefinedQuery] from the text entered by the user.
 *
 * Surrounding whitespace is removed, blank data elements and namespaces without data elements are left out, and
 * namespaces entered more than once are merged.
 *
 * @param name the name of the query, or a blank string for none.
 * @param docType the document type.
 * @param namespaces the namespace names with the data element names entered for each, in order.
 * @return the query, or why it cannot be built.
 */
internal fun buildUserDefinedQuery(
    name: String,
    docType: String,
    namespaces: List<Pair<String, List<String>>>
): UserDefinedQueryInput {
    val cleanDocType = docType.trim()
    val namespacesMap = mutableMapOf<String, List<String>>()
    for ((namespace, elements) in namespaces) {
        val cleanNamespace = namespace.trim()
        val cleanElements = elements.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanNamespace.isNotEmpty() && cleanElements.isNotEmpty()) {
            namespacesMap[cleanNamespace] = ((namespacesMap[cleanNamespace] ?: emptyList()) + cleanElements).distinct()
        }
    }
    if (cleanDocType.isEmpty() || namespacesMap.isEmpty()) {
        return UserDefinedQueryInput.Invalid(
            missingDocType = cleanDocType.isEmpty(),
            missingDataElements = namespacesMap.isEmpty()
        )
    }
    return UserDefinedQueryInput.Valid(
        UserDefinedQuery(
            docType = cleanDocType,
            namespaces = namespacesMap,
            name = name.trim().ifEmpty { null }
        )
    )
}

/**
 * Screen for adding or editing a user defined query.
 *
 * @param initialQuery the saved query to edit, or `null` to add a new one.
 * @param onSaved called with the query when the user saves it.
 * @param onDeleted called when the user deletes the query, or `null` if it has not been saved yet.
 * @param onBackClicked called when the user leaves without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectUserDefinedQueryScreen(
    initialQuery: UserDefinedQuery?,
    onSaved: (query: UserDefinedQuery) -> Unit,
    onDeleted: (() -> Unit)?,
    onBackClicked: () -> Unit
) {
    val hazeState = remember { HazeState() }
    var name by remember { mutableStateOf(initialQuery?.name ?: "") }
    var docType by remember { mutableStateOf(initialQuery?.docType ?: "") }

    val namespaceEntries = remember {
        mutableStateListOf<NamespaceEntryState>().apply {
            if (initialQuery != null && initialQuery.namespaces.isNotEmpty()) {
                initialQuery.namespaces.forEach { (ns, elems) ->
                    add(NamespaceEntryState(ns, if (elems.isEmpty()) listOf("") else elems))
                }
            } else {
                add(NamespaceEntryState())
            }
        }
    }
    val input = buildUserDefinedQuery(
        name = name,
        docType = docType,
        namespaces = namespaceEntries.map { it.name to it.elements.toList() }
    )
    // Only point out what is missing once the user has tried to save.
    var saveAttempted by remember { mutableStateOf(false) }
    val invalid = if (saveAttempted) input as? UserDefinedQueryInput.Invalid else null

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = { Text(stringResource(R.string.select_user_defined_query_dialog_title)) },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                actions = {
                    if (onDeleted != null) {
                        IconButton(onClick = onDeleted) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.select_user_defined_query_delete)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(scrollState)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            Note(
                markdownString = stringResource(R.string.select_user_defined_query_note)
            )

            SegmentedItemList(title = stringResource(R.string.select_user_defined_query_dialog_name)) {
                SegmentedItemContainer {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.select_user_defined_query_dialog_name)) },
                        supportingText = { Text(stringResource(R.string.select_user_defined_query_dialog_name_description)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Document Type segmented item list
            SegmentedItemList(title = stringResource(R.string.select_user_defined_query_dialog_doc_type)) {
                SegmentedItemContainer {
                    OutlinedTextField(
                        value = docType,
                        onValueChange = { docType = it },
                        label = { Text(stringResource(R.string.select_user_defined_query_dialog_doc_type)) },
                        isError = invalid?.missingDocType == true,
                        supportingText = if (invalid?.missingDocType == true) {
                            { Text(stringResource(R.string.select_user_defined_query_error_doc_type)) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // SegmentedItemList for each namespace
            namespaceEntries.forEachIndexed { nsIndex, nsState ->
                SegmentedItemList(
                    title = if (nsState.name.isNotBlank()) nsState.name else stringResource(R.string.select_user_defined_query_dialog_namespace)
                ) {
                    // Namespace name input container
                    SegmentedItemContainer {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = nsState.name,
                                onValueChange = { nsState.name = it },
                                label = { Text(stringResource(R.string.select_user_defined_query_dialog_namespace)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            if (namespaceEntries.size > 1) {
                                IconButton(onClick = { namespaceEntries.removeAt(nsIndex) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }

                    // Data element containers
                    nsState.elements.forEachIndexed { elemIndex, elemValue ->
                        SegmentedItemContainer {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = elemValue,
                                    onValueChange = { nsState.elements[elemIndex] = it },
                                    label = { Text(stringResource(R.string.select_user_defined_query_dialog_data_element)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                if (nsState.elements.size > 1) {
                                    IconButton(onClick = { nsState.elements.removeAt(elemIndex) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add data element container
                    SegmentedItemContainer(
                        modifier = Modifier.clickable { nsState.elements.add("") }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.select_user_defined_query_dialog_add_data_element),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Add namespace button
            OutlinedButton(
                onClick = {
                    namespaceEntries.add(NamespaceEntryState("", listOf("")))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.select_user_defined_query_dialog_add_namespace))
            }

            if (invalid?.missingDataElements == true) {
                Text(
                    text = stringResource(R.string.select_user_defined_query_error_data_elements),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = {
                    saveAttempted = true
                    if (input is UserDefinedQueryInput.Valid) {
                        onSaved(input.query)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.select_user_defined_query_save))
            }
        }
    }
}
