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
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.multipaz.compose.items.FloatingItemContainer
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.verification.UserDefinedQuery


private class NamespaceEntryState(
    name: String = "",
    elements: List<String> = listOf("")
) {
    var name by mutableStateOf(name)
    val elements = mutableStateListOf<String>().apply { addAll(elements) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectUserDefinedQueryScreen(
    initialQuery: UserDefinedQuery?,
    onConfirmed: (query: UserDefinedQuery) -> Unit,
    onBackClicked: () -> Unit
) {
    var docType by remember {
        mutableStateOf(initialQuery?.docType ?: org.multipaz.documenttype.knowntypes.PhotoID.PHOTO_ID_DOCTYPE)
    }

    val namespaceEntries = remember {
        mutableStateListOf<NamespaceEntryState>().apply {
            if (initialQuery != null && initialQuery.namespaces.isNotEmpty()) {
                initialQuery.namespaces.forEach { (ns, elems) ->
                    add(NamespaceEntryState(ns, if (elems.isEmpty()) listOf("") else elems))
                }
            } else {
                add(
                    NamespaceEntryState(
                        org.multipaz.documenttype.knowntypes.PhotoID.ISO_23220_2_NAMESPACE,
                        listOf("portrait", "age_in_years", "birth_date")
                    )
                )
            }
        }
    }

    // Instant-apply changes whenever docType or any namespace/element entry changes
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow {
            val cleanDocType = docType.trim()
            val namespacesMap = mutableMapOf<String, List<String>>()
            namespaceEntries.forEach { nsState ->
                val cleanNs = nsState.name.trim()
                if (cleanNs.isNotEmpty()) {
                    val cleanElems = nsState.elements
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (cleanElems.isNotEmpty()) {
                        namespacesMap[cleanNs] = cleanElems
                    }
                }
            }
            cleanDocType to namespacesMap
        }.collect { (cleanDocType, namespacesMap) ->
            if (cleanDocType.isNotEmpty() && namespacesMap.isNotEmpty()) {
                onConfirmed(UserDefinedQuery(docType = cleanDocType, namespaces = namespacesMap))
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.select_user_defined_query_dialog_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Note(
                markdownString = stringResource(R.string.select_user_defined_query_note)
            )

            // Document Type floating item list
            FloatingItemList(title = stringResource(R.string.select_user_defined_query_dialog_doc_type)) {
                FloatingItemContainer {
                    OutlinedTextField(
                        value = docType,
                        onValueChange = { docType = it },
                        label = { Text(stringResource(R.string.select_user_defined_query_dialog_doc_type)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // FloatingItemList for each namespace
            namespaceEntries.forEachIndexed { nsIndex, nsState ->
                FloatingItemList(
                    title = if (nsState.name.isNotBlank()) nsState.name else stringResource(R.string.select_user_defined_query_dialog_namespace)
                ) {
                    // Namespace name input container
                    FloatingItemContainer {
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
                        FloatingItemContainer {
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
                    FloatingItemContainer(
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
            Button(
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
