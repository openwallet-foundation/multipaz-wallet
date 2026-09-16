package org.multipaz.wallet.android.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.knowntypes.Options
import org.multipaz.securearea.SecureArea
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "DeveloperAddPhotoIdScreen"

private enum class ActiveDatePicker {
    DATE_OF_BIRTH,
    ISSUE_DATE,
    EXPIRY_DATE
}

data class AuthorizedDataElementEntry(
    val namespace: String,
    val dataElement: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperAddPhotoIdScreen(
    documentStore: DocumentStore,
    secureArea: SecureArea,
    userIssuerTrustManager: TrustManager,
    settingsModel: SettingsModel,
    onPhotoIdCreated: (Document) -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    val hazeState = remember { HazeState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val successToastMessage = stringResource(R.string.dev_add_photo_id_success)
    val errorToastTemplate = stringResource(R.string.dev_add_photo_id_error)

    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val defaultDob = remember {
        LocalDate(today.year - 20, today.month, today.day)
    }
    val defaultExpiry = remember {
        LocalDate(today.year + 5, today.month, today.day)
    }

    var givenName by remember { mutableStateOf("") }
    var familyName by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf<LocalDate?>(defaultDob) }
    var dateOfBirthText by remember { mutableStateOf(defaultDob.toString()) }

    var sex by remember { mutableStateOf(1L) }
    var sexExpanded by remember { mutableStateOf(false) }

    var nationality by remember { mutableStateOf("US") }
    var nationalityExpanded by remember { mutableStateOf(false) }
    var nationalitySearchQuery by remember { mutableStateOf("") }

    var documentNumber by remember { mutableStateOf("987654321") }

    var issueDate by remember { mutableStateOf<LocalDate?>(today) }
    var issueDateText by remember { mutableStateOf(today.toString()) }

    var expiryDate by remember { mutableStateOf<LocalDate?>(defaultExpiry) }
    var expiryDateText by remember { mutableStateOf(defaultExpiry.toString()) }

    var issuingAuthority by remember { mutableStateOf("Multipaz Wallet TEST In-App Issuing Authority") }
    var issuingCountry by remember { mutableStateOf("ZZ") }

    var personId by remember { mutableStateOf("24601") }
    var administrativeNumber by remember { mutableStateOf("123456789") }
    var residentStreet by remember { mutableStateOf("Main Street") }
    var residentHouseNumber by remember { mutableStateOf("123") }
    var residentCity by remember { mutableStateOf("Sample City") }
    var residentState by remember { mutableStateOf("CA") }
    var residentPostalCode by remember { mutableStateOf("12345") }
    var residentCountry by remember { mutableStateOf("US") }
    var residentAddress by remember { mutableStateOf("123 Main Street, Sample City, CA 12345") }

    var authorizedNamespaces by remember { mutableStateOf(listOf<String>()) }
    var authorizedDataElements by remember {
        mutableStateOf(
            listOf(
                AuthorizedDataElementEntry(
                    namespace = "org.iso.23220.5.1",
                    dataElement = "CHV_1"
                )
            )
        )
    }
    var newNamespaceInput by remember { mutableStateOf("") }
    var newDeNamespaceInput by remember { mutableStateOf("org.iso.23220.5.1") }
    var newDeDataElementInput by remember { mutableStateOf("") }

    val hasKeyAuthConflict = remember(authorizedNamespaces, authorizedDataElements) {
        val authNsSet = authorizedNamespaces.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val deNsSet = authorizedDataElements.map { it.namespace.trim() }.filter { it.isNotEmpty() }.toSet()
        authNsSet.intersect(deNsSet).isNotEmpty()
    }

    var activeDatePicker by remember { mutableStateOf<ActiveDatePicker?>(null) }

    val sexOptions = remember {
        listOfNotNull(1, 2, 0, 9).mapNotNull { code ->
            Options.SEX_ISO_IEC_5218.find { it.value == code }
        }
    }
    val countryOptions = remember {
        Options.COUNTRY_ISO_3166_1_ALPHA_2.filter { it.value != null }
    }

    val cameraPermissionState = rememberCameraPermissionState()
    val captureRequested = remember { AtomicBoolean(false) }
    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var capturedImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    var isCreating by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = {
                    Text(stringResource(R.string.dev_add_photo_id_screen_title))
                },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
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
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Note(
                markdownString = stringResource(R.string.dev_add_photo_id_explainer)
            )

            OutlinedTextField(
                value = givenName,
                onValueChange = { givenName = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_given_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = familyName,
                onValueChange = { familyName = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_family_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = dateOfBirthText,
                onValueChange = { input ->
                    dateOfBirthText = input
                    dateOfBirth = try {
                        LocalDate.parse(input.trim())
                    } catch (_: Exception) {
                        null
                    }
                },
                label = { Text(stringResource(R.string.dev_add_photo_id_date_of_birth)) },
                placeholder = { Text(stringResource(R.string.dev_add_photo_id_date_of_birth_placeholder)) },
                isError = dateOfBirthText.isNotBlank() && dateOfBirth == null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { activeDatePicker = ActiveDatePicker.DATE_OF_BIRTH }) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = stringResource(R.string.dev_add_photo_id_date_picker_title)
                        )
                    }
                }
            )

            val selectedSexOption = sexOptions.find { it.value?.toLong() == sex }
                ?: sexOptions.first()

            ExposedDropdownMenuBox(
                expanded = sexExpanded,
                onExpandedChange = { sexExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedSexOption.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.dev_add_photo_id_sex)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sexExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = sexExpanded,
                    onDismissRequest = { sexExpanded = false }
                ) {
                    sexOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                sex = option.value!!.toLong()
                                sexExpanded = false
                            }
                        )
                    }
                }
            }

            val selectedCountry = countryOptions.find { it.value == nationality }
            val selectedCountryDisplay = selectedCountry?.let { "${it.displayName} (${it.value})" } ?: nationality

            val filteredCountries = remember(nationalitySearchQuery, countryOptions) {
                if (nationalitySearchQuery.isBlank()) {
                    countryOptions
                } else {
                    countryOptions.filter {
                        it.displayName.contains(nationalitySearchQuery, ignoreCase = true) ||
                                (it.value?.contains(nationalitySearchQuery, ignoreCase = true) == true)
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = nationalityExpanded,
                onExpandedChange = {
                    nationalityExpanded = it
                    if (!it) {
                        nationalitySearchQuery = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCountryDisplay,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.dev_add_photo_id_nationality)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = nationalityExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = nationalityExpanded,
                    onDismissRequest = {
                        nationalityExpanded = false
                        nationalitySearchQuery = ""
                    },
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    OutlinedTextField(
                        value = nationalitySearchQuery,
                        onValueChange = { nationalitySearchQuery = it },
                        placeholder = { Text(stringResource(R.string.dev_add_photo_id_search_nationality)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (nationalitySearchQuery.isNotEmpty()) {
                                IconButton(onClick = { nationalitySearchQuery = "" }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    if (filteredCountries.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No matches") },
                            onClick = {},
                            enabled = false
                        )
                    } else {
                        filteredCountries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option.displayName} (${option.value})") },
                                onClick = {
                                    nationality = option.value!!
                                    nationalityExpanded = false
                                    nationalitySearchQuery = ""
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = documentNumber,
                onValueChange = { documentNumber = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_document_number)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = administrativeNumber,
                onValueChange = { administrativeNumber = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_administrative_number)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = personId,
                onValueChange = { personId = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_person_id)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = issueDateText,
                onValueChange = { input ->
                    issueDateText = input
                    issueDate = try {
                        LocalDate.parse(input.trim())
                    } catch (_: Exception) {
                        null
                    }
                },
                label = { Text(stringResource(R.string.dev_add_photo_id_issue_date)) },
                placeholder = { Text(stringResource(R.string.dev_add_photo_id_date_of_birth_placeholder)) },
                isError = issueDateText.isNotBlank() && issueDate == null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { activeDatePicker = ActiveDatePicker.ISSUE_DATE }) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = stringResource(R.string.dev_add_photo_id_date_picker_issue_title)
                        )
                    }
                }
            )

            OutlinedTextField(
                value = expiryDateText,
                onValueChange = { input ->
                    expiryDateText = input
                    expiryDate = try {
                        LocalDate.parse(input.trim())
                    } catch (_: Exception) {
                        null
                    }
                },
                label = { Text(stringResource(R.string.dev_add_photo_id_expiry_date)) },
                placeholder = { Text(stringResource(R.string.dev_add_photo_id_date_of_birth_placeholder)) },
                isError = expiryDateText.isNotBlank() && expiryDate == null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { activeDatePicker = ActiveDatePicker.EXPIRY_DATE }) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = stringResource(R.string.dev_add_photo_id_date_picker_expiry_title)
                        )
                    }
                }
            )

            OutlinedTextField(
                value = issuingAuthority,
                onValueChange = { issuingAuthority = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_issuing_authority)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = issuingCountry,
                onValueChange = { issuingCountry = it.uppercase() },
                label = { Text(stringResource(R.string.dev_add_photo_id_issuing_country)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = residentStreet,
                onValueChange = { residentStreet = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_resident_street)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = residentHouseNumber,
                onValueChange = { residentHouseNumber = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_resident_house_number)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = residentCity,
                onValueChange = { residentCity = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_resident_city)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = residentState,
                onValueChange = { residentState = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_resident_state)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = residentPostalCode,
                onValueChange = { residentPostalCode = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_resident_postal_code)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = residentCountry,
                onValueChange = { residentCountry = it.uppercase() },
                label = { Text(stringResource(R.string.dev_add_photo_id_resident_country)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = residentAddress,
                onValueChange = { residentAddress = it },
                label = { Text(stringResource(R.string.dev_add_photo_id_resident_address)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                )
            )

            Text(
                text = stringResource(R.string.dev_add_photo_id_key_authorizations_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.dev_add_photo_id_key_authorizations_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            // Authorized Name Spaces Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dev_add_photo_id_auth_namespaces_title),
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (authorizedNamespaces.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dev_add_photo_id_no_auth_namespaces),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        authorizedNamespaces.forEachIndexed { index, ns ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ns,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            authorizedNamespaces = authorizedNamespaces.toMutableList().also { it.removeAt(index) }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.dev_add_photo_id_remove_auth_namespace)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newNamespaceInput,
                            onValueChange = { newNamespaceInput = it },
                            label = { Text(stringResource(R.string.dev_add_photo_id_new_namespace_label)) },
                            placeholder = { Text("e.g. org.iso.23220.5.1") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = {
                                val trimmed = newNamespaceInput.trim()
                                if (trimmed.isNotEmpty() && !authorizedNamespaces.contains(trimmed)) {
                                    authorizedNamespaces = authorizedNamespaces + trimmed
                                    newNamespaceInput = ""
                                }
                            },
                            enabled = newNamespaceInput.isNotBlank()
                        ) {
                            Text(stringResource(R.string.dev_add_photo_id_add_button))
                        }
                    }
                }
            }

            // Authorized Data Elements Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dev_add_photo_id_auth_data_elements_title),
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (authorizedDataElements.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dev_add_photo_id_no_auth_data_elements),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        authorizedDataElements.forEachIndexed { index, entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.namespace,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = entry.dataElement,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            authorizedDataElements = authorizedDataElements.toMutableList().also { it.removeAt(index) }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.dev_add_photo_id_remove_auth_data_element)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newDeNamespaceInput,
                                onValueChange = { newDeNamespaceInput = it },
                                label = { Text(stringResource(R.string.dev_add_photo_id_new_data_element_ns_label)) },
                                placeholder = { Text("e.g. org.iso.23220.5.1") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = newDeDataElementInput,
                                onValueChange = { newDeDataElementInput = it },
                                label = { Text(stringResource(R.string.dev_add_photo_id_new_data_element_de_label)) },
                                placeholder = { Text("e.g. CHV_1") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                val trimmedNs = newDeNamespaceInput.trim()
                                val trimmedDe = newDeDataElementInput.trim()
                                if (trimmedNs.isNotEmpty() && trimmedDe.isNotEmpty()) {
                                    val entry = AuthorizedDataElementEntry(trimmedNs, trimmedDe)
                                    if (!authorizedDataElements.contains(entry)) {
                                        authorizedDataElements = authorizedDataElements + entry
                                        newDeDataElementInput = ""
                                    }
                                }
                            },
                            enabled = newDeNamespaceInput.isNotBlank() && newDeDataElementInput.isNotBlank(),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.dev_add_photo_id_add_data_element_button))
                        }
                    }
                }
            }

            if (hasKeyAuthConflict) {
                Text(
                    text = stringResource(R.string.dev_add_photo_id_key_auth_conflict_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = stringResource(R.string.dev_add_photo_id_portrait_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            if (!cameraPermissionState.isGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dev_add_photo_id_camera_permission_needed),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    cameraPermissionState.launchPermissionRequest()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.dev_add_photo_id_grant_permission))
                        }
                    }
                }
            } else if (capturedImageBitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Camera(
                        modifier = Modifier.fillMaxSize(),
                        cameraSelection = CameraSelection.DEFAULT_FRONT_CAMERA,
                        captureResolution = CameraCaptureResolution.MEDIUM,
                        showCameraPreview = true,
                        onFrameCaptured = { frame ->
                            if (captureRequested.compareAndSet(true, false)) {
                                val imageProxy = frame.cameraImage.imageProxy
                                val rawBitmap = imageProxy.toBitmap()
                                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                val matrix = android.graphics.Matrix().apply {
                                    postRotate(rotationDegrees.toFloat())
                                    postScale(-1f, 1f) // Mirror horizontally for front-facing selfie
                                }
                                val rotated = Bitmap.createBitmap(
                                    rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                                )
                                // Center crop to 3:4 aspect ratio
                                val targetWidth: Int
                                val targetHeight: Int
                                val startX: Int
                                val startY: Int
                                if (rotated.height * 3 > rotated.width * 4) {
                                    targetWidth = rotated.width
                                    targetHeight = rotated.width * 4 / 3
                                    startX = 0
                                    startY = (rotated.height - targetHeight) / 2
                                } else {
                                    targetWidth = rotated.height * 3 / 4
                                    targetHeight = rotated.height
                                    startX = (rotated.width - targetWidth) / 2
                                    startY = 0
                                }
                                val cropped = Bitmap.createBitmap(
                                    rotated, startX, startY, targetWidth, targetHeight
                                )
                                val scaled = Bitmap.createScaledBitmap(cropped, 480, 640, true)
                                val stream = ByteArrayOutputStream()
                                scaled.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                                val bytes = stream.toByteArray()
                                val composeBitmap = scaled.asImageBitmap()
                                withContext(Dispatchers.Main) {
                                    capturedBytes = bytes
                                    capturedImageBitmap = composeBitmap
                                }
                            }
                        }
                    )
                }

                FilledTonalButton(
                    onClick = {
                        captureRequested.set(true)
                    }
                ) {
                    Icon(imageVector = Icons.Outlined.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dev_add_photo_id_take_photo))
                }
            } else {
                capturedImageBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.dev_add_photo_id_portrait_captured),
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                OutlinedButton(
                    onClick = {
                        capturedBytes = null
                        capturedImageBitmap = null
                    }
                ) {
                    Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dev_add_photo_id_retake_photo))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val canCreate = givenName.isNotBlank() &&
                    familyName.isNotBlank() &&
                    dateOfBirth != null &&
                    issueDate != null &&
                    expiryDate != null &&
                    documentNumber.isNotBlank() &&
                    issuingAuthority.isNotBlank() &&
                    issuingCountry.isNotBlank() &&
                    capturedBytes != null &&
                    !hasKeyAuthConflict &&
                    !isCreating

            Button(
                onClick = {
                    val dob = dateOfBirth ?: return@Button
                    val issDate = issueDate ?: return@Button
                    val expDate = expiryDate ?: return@Button
                    val portrait = capturedBytes ?: return@Button
                    val authDeMap = authorizedDataElements
                        .map { AuthorizedDataElementEntry(it.namespace.trim(), it.dataElement.trim()) }
                        .filter { it.namespace.isNotEmpty() && it.dataElement.isNotEmpty() }
                        .groupBy({ it.namespace }, { it.dataElement })
                        .mapValues { it.value.distinct() }
                    val authNsList = authorizedNamespaces
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    isCreating = true
                    coroutineScope.launch {
                        try {
                            val createdDocument = DeveloperPhotoIdIssuer.issuePhotoId(
                                documentStore = documentStore,
                                secureArea = secureArea,
                                userIssuerTrustManager = userIssuerTrustManager,
                                settingsModel = settingsModel,
                                givenName = givenName.trim(),
                                familyName = familyName.trim(),
                                dateOfBirth = dob,
                                portraitBytes = portrait,
                                sex = sex,
                                nationality = nationality.trim(),
                                documentNumber = documentNumber.trim(),
                                issueDate = issDate,
                                expiryDate = expDate,
                                issuingAuthority = issuingAuthority.trim(),
                                issuingCountry = issuingCountry.trim(),
                                residentStreet = residentStreet.trim(),
                                residentHouseNumber = residentHouseNumber.trim(),
                                residentCity = residentCity.trim(),
                                residentState = residentState.trim(),
                                residentPostalCode = residentPostalCode.trim(),
                                residentCountry = residentCountry.trim(),
                                residentAddress = residentAddress.trim(),
                                personId = personId.trim(),
                                administrativeNumber = administrativeNumber.trim(),
                                authorizedNamespaces = authNsList,
                                authorizedDataElements = authDeMap
                            )
                            showToast(successToastMessage)
                            onPhotoIdCreated(createdDocument)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Logger.e(TAG, "Failed to create Photo ID", e)
                            showToast(String.format(errorToastTemplate, e.message ?: e.toString()))
                        } finally {
                            isCreating = false
                        }
                    }
                },
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.dev_add_photo_id_create_button))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    activeDatePicker?.let { currentPicker ->
        val initialDate = when (currentPicker) {
            ActiveDatePicker.DATE_OF_BIRTH -> dateOfBirth ?: defaultDob
            ActiveDatePicker.ISSUE_DATE -> issueDate ?: today
            ActiveDatePicker.EXPIRY_DATE -> expiryDate ?: defaultExpiry
        }
        val initialMillis = initialDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

        key(currentPicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = initialMillis
            )

            DatePickerDialog(
                onDismissRequest = { activeDatePicker = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val pickedDate = Instant.fromEpochMilliseconds(millis)
                                    .toLocalDateTime(TimeZone.UTC).date
                                when (currentPicker) {
                                    ActiveDatePicker.DATE_OF_BIRTH -> {
                                        dateOfBirth = pickedDate
                                        dateOfBirthText = pickedDate.toString()
                                    }
                                    ActiveDatePicker.ISSUE_DATE -> {
                                        issueDate = pickedDate
                                        issueDateText = pickedDate.toString()
                                    }
                                    ActiveDatePicker.EXPIRY_DATE -> {
                                        expiryDate = pickedDate
                                        expiryDateText = pickedDate.toString()
                                    }
                                }
                            }
                            activeDatePicker = null
                        }
                    ) {
                        Text(stringResource(R.string.error_dialog_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDatePicker = null }) {
                        Text(stringResource(R.string.confirmation_dialog_cancel))
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}
