package org.multipaz.wallet.android.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborInt
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.toDataItem
import org.multipaz.cbor.toDataItemDateTimeString
import org.multipaz.cbor.toDataItemFullDate
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.items.SegmentedItemContainer
import org.multipaz.compose.items.SegmentedItemList
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentAttribute
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.ISO_23220_5_CHV_1_DATA_ELEMENT
import org.multipaz.documenttype.ISO_23220_5_CHV_1_NAMESPACE
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.securearea.SecureArea
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.Logger
import org.multipaz.util.fromHex
import org.multipaz.util.toHex
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "DeveloperAddSelfSignedDocumentScreen"

private val AGE_OVER_REGEX = Regex("^age_(?:over_|above)(\\d+)$")
private val JSON_AGE_OVER_REGEX = Regex("^age_equal_or_over\\.(\\d+)$")

data class AuthorizedDataElementEntry(
    val namespace: String,
    val dataElement: String
)

private data class DatePickerRequest(
    val currentText: String,
    val isDateTime: Boolean,
    val onDatePicked: (LocalDate) -> Unit
)

private data class EditableAttributeState(
    val attribute: DocumentAttribute,
    val mandatory: Boolean,
    val isJson: Boolean,
    val included: Boolean,
    val textValue: String = "",
    val boolValue: Boolean = false,
    val intOptionValue: Int? = null
) {
    fun isValid(): Boolean {
        if (!included) return true
        return when (attribute.type) {
            is DocumentAttributeType.String -> !mandatory || textValue.isNotBlank()
            is DocumentAttributeType.Number -> textValue.trim().toLongOrNull() != null
            is DocumentAttributeType.Boolean -> true
            is DocumentAttributeType.Date -> runCatching { LocalDate.parse(textValue.trim()) }.isSuccess
            is DocumentAttributeType.DateTime -> runCatching { Instant.parse(textValue.trim()) }.isSuccess
            is DocumentAttributeType.IntegerOptions -> intOptionValue != null
            is DocumentAttributeType.StringOptions -> textValue.isNotBlank()
            is DocumentAttributeType.Blob,
            is DocumentAttributeType.Picture -> {
                if (isJson) {
                    true
                } else {
                    runCatching { textValue.trim().fromHex() }.isSuccess
                }
            }
            is DocumentAttributeType.ComplexType -> {
                if (isJson) {
                    runCatching { Json.parseToJsonElement(textValue.trim()) }.isSuccess
                } else {
                    runCatching { Cbor.decode(textValue.trim().fromHex()) }.isSuccess
                }
            }
        }
    }

    fun toDataItem(): DataItem {
        return when (attribute.type) {
            is DocumentAttributeType.String -> textValue.trim().toDataItem()
            is DocumentAttributeType.Number -> textValue.trim().toLong().toDataItem()
            is DocumentAttributeType.Boolean -> boolValue.toDataItem()
            is DocumentAttributeType.Date -> {
                val localDate = LocalDate.parse(textValue.trim())
                if (attribute.sampleValueMdoc is CborMap) {
                    buildCborMap { put("birth_date", localDate.toDataItemFullDate()) }
                } else {
                    localDate.toDataItemFullDate()
                }
            }
            is DocumentAttributeType.DateTime -> Instant.parse(textValue.trim()).toDataItemDateTimeString()
            is DocumentAttributeType.IntegerOptions -> (intOptionValue ?: 0).toLong().toDataItem()
            is DocumentAttributeType.StringOptions -> textValue.trim().toDataItem()
            is DocumentAttributeType.Blob,
            is DocumentAttributeType.Picture -> textValue.trim().fromHex().toDataItem()
            is DocumentAttributeType.ComplexType -> Cbor.decode(textValue.trim().fromHex())
        }
    }

    fun toJsonElement(): JsonElement {
        return when (attribute.type) {
            is DocumentAttributeType.String -> JsonPrimitive(textValue.trim())
            is DocumentAttributeType.Number -> JsonPrimitive(textValue.trim().toLong())
            is DocumentAttributeType.Boolean -> JsonPrimitive(boolValue)
            is DocumentAttributeType.Date -> JsonPrimitive(LocalDate.parse(textValue.trim()).toString())
            is DocumentAttributeType.DateTime -> JsonPrimitive(Instant.parse(textValue.trim()).toString())
            is DocumentAttributeType.IntegerOptions -> JsonPrimitive(intOptionValue ?: 0)
            is DocumentAttributeType.StringOptions -> JsonPrimitive(textValue.trim())
            is DocumentAttributeType.Blob,
            is DocumentAttributeType.Picture -> JsonPrimitive(textValue.trim())
            is DocumentAttributeType.ComplexType -> Json.parseToJsonElement(textValue.trim())
        }
    }
}

private fun createInitialMdocAttributeStates(
    documentType: DocumentType,
    today: LocalDate,
    defaultDob: LocalDate,
    defaultExpiry: LocalDate
): Map<Pair<String, String>, EditableAttributeState> {
    val mdocDocType = documentType.mdocDocumentType ?: return emptyMap()
    val peData = mdocDocType.supportsPortraitImageEquivalence
    val defaultToAge20 = DeveloperSelfSignedDocumentIssuer.shouldDefaultToAge20(documentType)
    val states = mutableMapOf<Pair<String, String>, EditableAttributeState>()

    for ((nsName, ns) in mdocDocType.namespaces) {
        for ((deName, de) in ns.dataElements) {
            if (peData != null && peData.namespace == nsName && peData.dataElementName == deName) {
                continue
            }
            val attr = de.attribute
            val sample = attr.sampleValueMdoc
            val included = de.mandatory || (sample != null && sample != Simple.NULL)
            states[Pair(nsName, deName)] = createMdocStateFromSample(
                attribute = attr,
                dataElementName = deName,
                mandatory = de.mandatory,
                included = included,
                today = today,
                defaultDob = defaultDob,
                defaultExpiry = defaultExpiry,
                defaultToAge20 = defaultToAge20
            )
        }
    }

    val birthDateText = states.entries
        .firstOrNull { (key, state) ->
            (key.second == "birth_date" || key.second == "dob") && state.included
        }
        ?.value?.textValue
    val parsedDob = birthDateText?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
    return if (parsedDob != null) {
        updateMdocAgeStates(states, parsedDob, today)
    } else {
        states
    }
}

private fun createMdocStateFromSample(
    attribute: DocumentAttribute,
    dataElementName: String,
    mandatory: Boolean,
    included: Boolean,
    today: LocalDate,
    defaultDob: LocalDate,
    defaultExpiry: LocalDate,
    defaultToAge20: Boolean
): EditableAttributeState {
    val sample = attribute.sampleValueMdoc
    return when (val type = attribute.type) {
        is DocumentAttributeType.String -> {
            val text = when {
                dataElementName == "issuing_authority" ->
                    DeveloperSelfSignedDocumentIssuer.DEFAULT_ISSUING_AUTHORITY
                dataElementName == "issuing_country" ->
                    DeveloperSelfSignedDocumentIssuer.DEFAULT_ISSUING_COUNTRY
                sample is Tstr -> sample.asTstr
                else -> ""
            }
            EditableAttributeState(attribute, mandatory, isJson = false, included = included, textValue = text)
        }
        is DocumentAttributeType.Number -> {
            val numStr = if (sample is CborInt) sample.asNumber.toString() else "0"
            EditableAttributeState(attribute, mandatory, isJson = false, included = included, textValue = numStr)
        }
        is DocumentAttributeType.Boolean -> {
            val b = if (sample is Simple && sample == Simple.TRUE) true else false
            EditableAttributeState(attribute, mandatory, isJson = false, included = included, boolValue = b)
        }
        is DocumentAttributeType.Date -> {
            val dateStr = when {
                dataElementName == "issue_date" ||
                    dataElementName == "issuance_date" ||
                    dataElementName == "portrait_capture_date" -> today.toString()
                dataElementName == "expiry_date" -> defaultExpiry.toString()
                defaultToAge20 && dataElementName == "birth_date" -> defaultDob.toString()
                sample is CborMap && sample.hasKey("birth_date") ->
                    runCatching { sample["birth_date"].asDateStringOrDateTimeString.toString() }
                        .getOrDefault(defaultDob.toString())
                sample is Tagged ->
                    runCatching { sample.asDateStringOrDateTimeString.toString() }
                        .getOrDefault(defaultDob.toString())
                dataElementName == "birth_date" -> defaultDob.toString()
                else -> today.toString()
            }
            EditableAttributeState(attribute, mandatory, isJson = false, included = included, textValue = dateStr)
        }
        is DocumentAttributeType.DateTime -> {
            val dtStr = when {
                sample is Tagged && sample.tagNumber == Tagged.DATE_TIME_STRING ->
                    runCatching { sample.asDateTimeString.toString() }.getOrDefault("${today}T00:00:00Z")
                sample is Tagged && sample.tagNumber == Tagged.FULL_DATE_STRING ->
                    runCatching { "${sample.asDateString}T00:00:00Z" }.getOrDefault("${today}T00:00:00Z")
                else -> "${today}T00:00:00Z"
            }
            EditableAttributeState(attribute, mandatory, isJson = false, included = included, textValue = dtStr)
        }
        is DocumentAttributeType.IntegerOptions -> {
            val nonNullOpts = type.options.filter { it.value != null }
            val intVal = (sample as? CborInt)?.asNumber?.toInt()
                ?: nonNullOpts.firstOrNull()?.value
            EditableAttributeState(attribute, mandatory, isJson = false, included = included, intOptionValue = intVal)
        }
        is DocumentAttributeType.StringOptions -> {
            val nonNullOpts = type.options.filter { it.value != null }
            val strVal = when {
                dataElementName == "issuing_country" ->
                    DeveloperSelfSignedDocumentIssuer.DEFAULT_ISSUING_COUNTRY
                sample is Tstr -> sample.asTstr
                else -> nonNullOpts.firstOrNull()?.value ?: ""
            }
            EditableAttributeState(attribute, mandatory, isJson = false, included = included, textValue = strVal)
        }
        is DocumentAttributeType.Blob,
        is DocumentAttributeType.Picture -> {
            val hex = (sample as? Bstr)?.asBstr?.toHex() ?: ""
            EditableAttributeState(attribute, mandatory, isJson = false, included = included, textValue = hex)
        }
        is DocumentAttributeType.ComplexType -> {
            val hex = if (sample != null && sample != Simple.NULL) {
                Cbor.encode(sample).toHex()
            } else {
                Cbor.encode(buildCborMap {}).toHex()
            }
            EditableAttributeState(attribute, mandatory, isJson = false, included = included, textValue = hex)
        }
    }
}

private fun createInitialJsonAttributeStates(
    documentType: DocumentType,
    today: LocalDate,
    defaultDob: LocalDate,
    defaultExpiry: LocalDate
): Map<String, EditableAttributeState> {
    val jsonDocType = documentType.jsonDocumentType ?: return emptyMap()
    val portraitClaimName = DeveloperSelfSignedDocumentIssuer.findJsonPortraitClaimName(jsonDocType)
    val defaultToAge20 = DeveloperSelfSignedDocumentIssuer.shouldDefaultToAge20(documentType)
    val states = mutableMapOf<String, EditableAttributeState>()

    for ((claimName, attribute) in jsonDocType.claims) {
        if (claimName.contains('.') || claimName == portraitClaimName) {
            continue
        }
        if (attribute.embeddedAttributes.isNotEmpty()) {
            val parentSampleObj = attribute.sampleValueJson as? JsonObject
            for (subAttr in attribute.embeddedAttributes) {
                val path = "$claimName.${subAttr.identifier}"
                val subSample = parentSampleObj?.get(subAttr.identifier)
                    ?: subAttr.sampleValueJson
                    ?: DeveloperSelfSignedDocumentIssuer.findFallbackSampleJson(documentType, subAttr.identifier)
                val included = subSample != null && subSample !is JsonNull
                states[path] = createJsonStateFromSample(
                    attribute = subAttr,
                    claimIdentifier = subAttr.identifier,
                    sample = subSample,
                    included = included,
                    today = today,
                    defaultDob = defaultDob,
                    defaultExpiry = defaultExpiry,
                    defaultToAge20 = defaultToAge20
                )
            }
        } else {
            val sample = attribute.sampleValueJson
                ?: DeveloperSelfSignedDocumentIssuer.findFallbackSampleJson(documentType, claimName)
            val included = sample != null && sample !is JsonNull
            states[claimName] = createJsonStateFromSample(
                attribute = attribute,
                claimIdentifier = claimName,
                sample = sample,
                included = included,
                today = today,
                defaultDob = defaultDob,
                defaultExpiry = defaultExpiry,
                defaultToAge20 = defaultToAge20
            )
        }
    }

    val birthDateText = (states["birthdate"] ?: states["birth_date"])
        ?.takeIf { it.included }?.textValue
    val parsedDob = birthDateText?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
    return if (parsedDob != null) {
        updateJsonAgeStates(states, parsedDob, today)
    } else {
        states
    }
}

private fun createJsonStateFromSample(
    attribute: DocumentAttribute,
    claimIdentifier: String,
    sample: JsonElement?,
    included: Boolean,
    today: LocalDate,
    defaultDob: LocalDate,
    defaultExpiry: LocalDate,
    defaultToAge20: Boolean
): EditableAttributeState {
    val primContent = (sample as? JsonPrimitive)?.content
    return when (val type = attribute.type) {
        is DocumentAttributeType.String -> {
            val text = when {
                claimIdentifier == "issuing_authority" ->
                    DeveloperSelfSignedDocumentIssuer.DEFAULT_ISSUING_AUTHORITY
                claimIdentifier == "issuing_country" ->
                    DeveloperSelfSignedDocumentIssuer.DEFAULT_ISSUING_COUNTRY
                else -> primContent ?: ""
            }
            EditableAttributeState(attribute, mandatory = false, isJson = true, included = included, textValue = text)
        }
        is DocumentAttributeType.Number -> {
            EditableAttributeState(
                attribute = attribute,
                mandatory = false,
                isJson = true,
                included = included,
                textValue = primContent ?: "0"
            )
        }
        is DocumentAttributeType.Boolean -> {
            val b = primContent?.toBooleanStrictOrNull() ?: false
            EditableAttributeState(attribute, mandatory = false, isJson = true, included = included, boolValue = b)
        }
        is DocumentAttributeType.Date -> {
            val dateStr = when {
                claimIdentifier == "issue_date" || claimIdentifier == "date_of_issuance" -> today.toString()
                claimIdentifier == "expiry_date" || claimIdentifier == "date_of_expiry" -> defaultExpiry.toString()
                defaultToAge20 && (claimIdentifier == "birth_date" || claimIdentifier == "birthdate") ->
                    defaultDob.toString()
                primContent != null && runCatching { LocalDate.parse(primContent) }.isSuccess -> primContent
                claimIdentifier == "birth_date" || claimIdentifier == "birthdate" -> defaultDob.toString()
                else -> today.toString()
            }
            EditableAttributeState(attribute, mandatory = false, isJson = true, included = included, textValue = dateStr)
        }
        is DocumentAttributeType.DateTime -> {
            val dtStr = if (primContent != null && runCatching { Instant.parse(primContent) }.isSuccess) {
                primContent
            } else {
                "${today}T00:00:00Z"
            }
            EditableAttributeState(attribute, mandatory = false, isJson = true, included = included, textValue = dtStr)
        }
        is DocumentAttributeType.IntegerOptions -> {
            val nonNullOpts = type.options.filter { it.value != null }
            val intVal = primContent?.toIntOrNull() ?: nonNullOpts.firstOrNull()?.value
            EditableAttributeState(attribute, mandatory = false, isJson = true, included = included, intOptionValue = intVal)
        }
        is DocumentAttributeType.StringOptions -> {
            val nonNullOpts = type.options.filter { it.value != null }
            val strVal = when {
                claimIdentifier == "issuing_country" ->
                    DeveloperSelfSignedDocumentIssuer.DEFAULT_ISSUING_COUNTRY
                else -> primContent ?: nonNullOpts.firstOrNull()?.value ?: ""
            }
            EditableAttributeState(attribute, mandatory = false, isJson = true, included = included, textValue = strVal)
        }
        is DocumentAttributeType.Blob,
        is DocumentAttributeType.Picture -> {
            EditableAttributeState(
                attribute = attribute,
                mandatory = false,
                isJson = true,
                included = included,
                textValue = primContent ?: ""
            )
        }
        is DocumentAttributeType.ComplexType -> {
            val jsonStr = if (sample != null && sample !is JsonNull) sample.toString() else "{}"
            EditableAttributeState(attribute, mandatory = false, isJson = true, included = included, textValue = jsonStr)
        }
    }
}

private fun calculateAgeInYears(dob: LocalDate, today: LocalDate): Int {
    var age = today.year - dob.year
    if (today.month < dob.month || (today.month == dob.month && today.day < dob.day)) {
        age--
    }
    return maxOf(0, age)
}

private fun updateMdocAgeStates(
    states: Map<Pair<String, String>, EditableAttributeState>,
    dob: LocalDate,
    today: LocalDate
): Map<Pair<String, String>, EditableAttributeState> {
    val ageInYears = calculateAgeInYears(dob, today)
    val result = states.toMutableMap()
    for ((key, state) in states) {
        val deName = key.second
        when {
            deName == "age_in_years" && state.attribute.type == DocumentAttributeType.Number -> {
                result[key] = state.copy(textValue = ageInYears.toString())
            }
            deName == "age_birth_year" && state.attribute.type == DocumentAttributeType.Number -> {
                result[key] = state.copy(textValue = dob.year.toString())
            }
            state.attribute.type == DocumentAttributeType.Boolean -> {
                val match = AGE_OVER_REGEX.matchEntire(deName)
                val threshold = match?.groupValues?.get(1)?.toIntOrNull()
                if (threshold != null) {
                    result[key] = state.copy(boolValue = ageInYears >= threshold)
                }
            }
        }
    }
    return result
}

private fun updateJsonAgeStates(
    states: Map<String, EditableAttributeState>,
    dob: LocalDate,
    today: LocalDate
): Map<String, EditableAttributeState> {
    val ageInYears = calculateAgeInYears(dob, today)
    val result = states.toMutableMap()
    for ((path, state) in states) {
        when {
            path == "age_in_years" && state.attribute.type == DocumentAttributeType.Number -> {
                result[path] = state.copy(textValue = ageInYears.toString())
            }
            path == "age_birth_year" && state.attribute.type == DocumentAttributeType.Number -> {
                result[path] = state.copy(textValue = dob.year.toString())
            }
            state.attribute.type == DocumentAttributeType.Boolean -> {
                val match = JSON_AGE_OVER_REGEX.matchEntire(path) ?: AGE_OVER_REGEX.matchEntire(path)
                val threshold = match?.groupValues?.get(1)?.toIntOrNull()
                if (threshold != null) {
                    result[path] = state.copy(boolValue = ageInYears >= threshold)
                }
            }
        }
    }
    return result
}

private fun buildJsonClaimsFromStates(
    documentType: DocumentType,
    jsonStates: Map<String, EditableAttributeState>
): JsonObject {
    val jsonDocType = documentType.jsonDocumentType ?: return buildJsonObject {}
    val portraitClaimName = DeveloperSelfSignedDocumentIssuer.findJsonPortraitClaimName(jsonDocType)
    return buildJsonObject {
        for ((claimName, attribute) in jsonDocType.claims) {
            if (claimName.contains('.') || claimName == portraitClaimName) {
                continue
            }
            if (attribute.embeddedAttributes.isNotEmpty()) {
                val subObj = buildJsonObject {
                    for (subAttr in attribute.embeddedAttributes) {
                        val subState = jsonStates["$claimName.${subAttr.identifier}"]
                        if (subState != null && subState.included && subState.isValid()) {
                            put(subAttr.identifier, subState.toJsonElement())
                        }
                    }
                }
                if (subObj.isNotEmpty()) {
                    put(claimName, subObj)
                }
            } else {
                val state = jsonStates[claimName]
                if (state != null && state.included && state.isValid()) {
                    put(claimName, state.toJsonElement())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperAddSelfSignedDocumentScreen(
    documentStore: DocumentStore,
    secureArea: SecureArea,
    userIssuerTrustManager: TrustManager,
    settingsModel: SettingsModel,
    documentTypeRepository: DocumentTypeRepository,
    onDocumentCreated: (Document) -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    val hazeState = remember { HazeState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val successToastMessage = stringResource(R.string.dev_add_self_signed_success)
    val errorToastTemplate = stringResource(R.string.dev_add_self_signed_error)

    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val defaultDob = remember {
        today.minus(DeveloperSelfSignedDocumentIssuer.DEFAULT_HOLDER_AGE_YEARS, DateTimeUnit.YEAR)
    }
    val defaultExpiry = remember {
        today.plus(5, DateTimeUnit.YEAR)
    }

    val supportedDocumentTypes = remember(documentTypeRepository) {
        documentTypeRepository.documentTypes.filter {
            it.mdocDocumentType != null || it.jsonDocumentType != null
        }
    }

    var selectedDocumentType by remember(supportedDocumentTypes) {
        mutableStateOf(
            supportedDocumentTypes.firstOrNull {
                it.mdocDocumentType?.docType == PhotoID.PHOTO_ID_DOCTYPE
            } ?: supportedDocumentTypes.first()
        )
    }
    var docTypeDropdownExpanded by remember { mutableStateOf(false) }

    var mdocAttributeStates by remember(selectedDocumentType) {
        mutableStateOf(
            createInitialMdocAttributeStates(
                documentType = selectedDocumentType,
                today = today,
                defaultDob = defaultDob,
                defaultExpiry = defaultExpiry
            )
        )
    }
    var jsonAttributeStates by remember(selectedDocumentType) {
        mutableStateOf(
            createInitialJsonAttributeStates(
                documentType = selectedDocumentType,
                today = today,
                defaultDob = defaultDob,
                defaultExpiry = defaultExpiry
            )
        )
    }

    var authorizedNamespaces by remember(selectedDocumentType) {
        mutableStateOf(listOf<String>())
    }
    var authorizedDataElements by remember(selectedDocumentType) {
        mutableStateOf(
            if (selectedDocumentType.mdocDocumentType?.supportsPortraitImageEquivalence != null) {
                listOf(
                    AuthorizedDataElementEntry(
                        namespace = ISO_23220_5_CHV_1_NAMESPACE,
                        dataElement = ISO_23220_5_CHV_1_DATA_ELEMENT
                    )
                )
            } else {
                emptyList()
            }
        )
    }
    var newNamespaceInput by remember(selectedDocumentType) { mutableStateOf("") }
    var newDeNamespaceInput by remember(selectedDocumentType) {
        mutableStateOf(ISO_23220_5_CHV_1_NAMESPACE)
    }
    var newDeDataElementInput by remember(selectedDocumentType) { mutableStateOf("") }

    val hasKeyAuthConflict = remember(authorizedNamespaces, authorizedDataElements) {
        val authNsSet = authorizedNamespaces.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val deNsSet = authorizedDataElements.map { it.namespace.trim() }.filter { it.isNotEmpty() }.toSet()
        authNsSet.intersect(deNsSet).isNotEmpty()
    }

    var activeDatePickerRequest by remember { mutableStateOf<DatePickerRequest?>(null) }

    val cameraPermissionState = rememberCameraPermissionState()
    val captureRequested = remember { AtomicBoolean(false) }
    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var capturedImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    var isCreating by remember { mutableStateOf(false) }

    val mdocDocType = selectedDocumentType.mdocDocumentType
    val jsonDocType = selectedDocumentType.jsonDocumentType
    val mdocPeData = mdocDocType?.supportsPortraitImageEquivalence
    val jsonPortraitClaim = remember(jsonDocType) {
        DeveloperSelfSignedDocumentIssuer.findJsonPortraitClaimName(jsonDocType)
    }
    val supportsPortrait = mdocPeData != null || jsonPortraitClaim != null

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    fun onMdocStateUpdated(
        key: Pair<String, String>,
        newState: EditableAttributeState
    ) {
        var nextMdoc = mdocAttributeStates.toMutableMap().apply { put(key, newState) }.toMap()
        var nextJson = jsonAttributeStates
        val deName = key.second
        if ((deName == "birth_date" || deName == "dob") &&
            newState.attribute.type == DocumentAttributeType.Date
        ) {
            val dob = runCatching { LocalDate.parse(newState.textValue.trim()) }.getOrNull()
            if (dob != null) {
                nextMdoc = updateMdocAgeStates(nextMdoc, dob, today)
                val jsonDobKey = when {
                    nextJson.containsKey("birthdate") -> "birthdate"
                    nextJson.containsKey("birth_date") -> "birth_date"
                    else -> null
                }
                if (jsonDobKey != null) {
                    val existingJsonDob = nextJson[jsonDobKey]!!
                    nextJson = nextJson.toMutableMap().apply {
                        put(jsonDobKey, existingJsonDob.copy(textValue = dob.toString()))
                    }
                }
                nextJson = updateJsonAgeStates(nextJson, dob, today)
            }
        } else {
            val matchingJsonKey = when (deName) {
                "issuance_date" -> if (nextJson.containsKey("date_of_issuance")) "date_of_issuance" else deName
                "expiry_date" -> if (nextJson.containsKey("date_of_expiry")) "date_of_expiry" else deName
                "resident_address" -> if (nextJson.containsKey("address.formatted")) "address.formatted" else deName
                "resident_country" -> if (nextJson.containsKey("address.country")) "address.country" else deName
                "resident_state" -> if (nextJson.containsKey("address.region")) "address.region" else deName
                "resident_city" -> if (nextJson.containsKey("address.locality")) "address.locality" else deName
                "resident_postal_code" -> if (nextJson.containsKey("address.postal_code")) "address.postal_code" else deName
                "resident_street" -> if (nextJson.containsKey("address.street_address")) "address.street_address" else deName
                "resident_house_number" -> if (nextJson.containsKey("address.house_number")) "address.house_number" else deName
                else -> deName
            }
            val existingJsonState = nextJson[matchingJsonKey]
            if (existingJsonState != null && existingJsonState.attribute.type::class == newState.attribute.type::class) {
                nextJson = nextJson.toMutableMap().apply {
                    put(
                        matchingJsonKey,
                        existingJsonState.copy(
                            textValue = newState.textValue,
                            boolValue = newState.boolValue,
                            intOptionValue = newState.intOptionValue
                        )
                    )
                }
            }
        }
        mdocAttributeStates = nextMdoc
        jsonAttributeStates = nextJson
    }

    fun onJsonStateUpdated(
        path: String,
        newState: EditableAttributeState
    ) {
        var nextJson = jsonAttributeStates.toMutableMap().apply { put(path, newState) }.toMap()
        if ((path == "birthdate" || path == "birth_date") &&
            newState.attribute.type == DocumentAttributeType.Date
        ) {
            val dob = runCatching { LocalDate.parse(newState.textValue.trim()) }.getOrNull()
            if (dob != null) {
                nextJson = updateJsonAgeStates(nextJson, dob, today)
            }
        }
        jsonAttributeStates = nextJson
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = {
                    Text(stringResource(R.string.dev_add_self_signed_screen_title))
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
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            Note(
                markdownString = stringResource(R.string.dev_add_self_signed_explainer)
            )

            // Document Type Selector
            SegmentedItemList(
                title = stringResource(R.string.dev_add_self_signed_document_type_label)
            ) {
                SegmentedItemContainer {
                    ExposedDropdownMenuBox(
                        expanded = docTypeDropdownExpanded,
                        onExpandedChange = { docTypeDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val formatBadge = buildList {
                            selectedDocumentType.mdocDocumentType?.let { add(it.docType) }
                            selectedDocumentType.jsonDocumentType?.let { add(it.vct) }
                        }.joinToString(" • ")

                        OutlinedTextField(
                            value = "${selectedDocumentType.displayName} ($formatBadge)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.dev_add_self_signed_document_type_label)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = docTypeDropdownExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = docTypeDropdownExpanded,
                            onDismissRequest = { docTypeDropdownExpanded = false }
                        ) {
                            supportedDocumentTypes.forEach { docType ->
                                val docBadge = buildList {
                                    docType.mdocDocumentType?.let { add("mdoc: ${it.docType}") }
                                    docType.jsonDocumentType?.let { add("SD-JWT: ${it.vct}") }
                                }.joinToString(" • ")
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = docType.displayName,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = docBadge,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedDocumentType = docType
                                        docTypeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ISO mdoc section
            if (mdocDocType != null) {
                Text(
                    text = stringResource(R.string.dev_add_self_signed_mdoc_section_title, mdocDocType.docType),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                for ((nsName, ns) in mdocDocType.namespaces) {
                    key(selectedDocumentType.displayName, nsName) {
                        var showOptionalAttributes by remember { mutableStateOf(false) }
                        val allEntries = ns.dataElements.keys.mapNotNull { deName ->
                            val stateKey = Pair(nsName, deName)
                            mdocAttributeStates[stateKey]?.let { stateKey to it }
                        }
                        val activeEntries = allEntries.filter { (_, state) -> state.included || state.mandatory }
                        val optionalHiddenEntries = allEntries.filter { (_, state) -> !state.included && !state.mandatory }

                        SegmentedItemList(
                            title = stringResource(R.string.dev_add_self_signed_namespace_title, nsName)
                        ) {
                            activeEntries.forEach { (stateKey, state) ->
                                SegmentedItemContainer {
                                    AttributeEditorRow(
                                        identifier = stateKey.second,
                                        state = state,
                                        onStateChange = { updated -> onMdocStateUpdated(stateKey, updated) },
                                        onOpenDatePicker = { req -> activeDatePickerRequest = req }
                                    )
                                }
                            }

                            if (optionalHiddenEntries.isNotEmpty() || showOptionalAttributes) {
                                SegmentedItemContainer(
                                    modifier = Modifier.clickable {
                                        showOptionalAttributes = !showOptionalAttributes
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (showOptionalAttributes) {
                                                stringResource(R.string.dev_add_self_signed_hide_optional_attributes)
                                            } else {
                                                stringResource(
                                                    R.string.dev_add_self_signed_show_optional_attributes,
                                                    optionalHiddenEntries.size
                                                )
                                            },
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }

                                if (showOptionalAttributes) {
                                    optionalHiddenEntries.forEach { (stateKey, state) ->
                                        SegmentedItemContainer {
                                            AttributeEditorRow(
                                                identifier = stateKey.second,
                                                state = state,
                                                onStateChange = { updated -> onMdocStateUpdated(stateKey, updated) },
                                                onOpenDatePicker = { req -> activeDatePickerRequest = req }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // MSO Key Authorizations section
                Text(
                    text = stringResource(R.string.dev_add_self_signed_key_authorizations_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.dev_add_self_signed_key_authorizations_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                // Authorized Name Spaces
                SegmentedItemList(
                    title = stringResource(R.string.dev_add_self_signed_auth_namespaces_title)
                ) {
                    if (authorizedNamespaces.isEmpty()) {
                        SegmentedItemContainer {
                            Text(
                                text = stringResource(R.string.dev_add_self_signed_no_auth_namespaces),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        authorizedNamespaces.forEachIndexed { index, ns ->
                            SegmentedItemContainer {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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
                                            authorizedNamespaces = authorizedNamespaces
                                                .toMutableList()
                                                .also { it.removeAt(index) }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(
                                                R.string.dev_add_self_signed_remove_auth_namespace
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SegmentedItemContainer {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newNamespaceInput,
                                onValueChange = { newNamespaceInput = it },
                                label = { Text(stringResource(R.string.dev_add_self_signed_new_namespace_label)) },
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
                                Text(stringResource(R.string.dev_add_self_signed_add_button))
                            }
                        }
                    }
                }

                // Authorized Data Elements
                SegmentedItemList(
                    title = stringResource(R.string.dev_add_self_signed_auth_data_elements_title)
                ) {
                    if (authorizedDataElements.isEmpty()) {
                        SegmentedItemContainer {
                            Text(
                                text = stringResource(R.string.dev_add_self_signed_no_auth_data_elements),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        authorizedDataElements.forEachIndexed { index, entry ->
                            SegmentedItemContainer {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.namespace,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = entry.dataElement,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            authorizedDataElements = authorizedDataElements
                                                .toMutableList()
                                                .also { it.removeAt(index) }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(
                                                R.string.dev_add_self_signed_remove_auth_data_element
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SegmentedItemContainer {
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
                                    label = {
                                        Text(stringResource(R.string.dev_add_self_signed_new_data_element_ns_label))
                                    },
                                    placeholder = { Text("e.g. org.iso.23220.5.1") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = newDeDataElementInput,
                                    onValueChange = { newDeDataElementInput = it },
                                    label = {
                                        Text(stringResource(R.string.dev_add_self_signed_new_data_element_de_label))
                                    },
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
                                Text(stringResource(R.string.dev_add_self_signed_add_data_element_button))
                            }
                        }
                    }
                }

                if (hasKeyAuthConflict) {
                    Text(
                        text = stringResource(R.string.dev_add_self_signed_key_auth_conflict_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // SD-JWT VC section
            if (jsonDocType != null) {
                Text(
                    text = stringResource(R.string.dev_add_self_signed_sd_jwt_section_title, jsonDocType.vct),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (jsonDocType.keyBound) {
                        stringResource(R.string.dev_add_self_signed_sd_jwt_key_bound)
                    } else {
                        stringResource(R.string.dev_add_self_signed_sd_jwt_keyless)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                key(selectedDocumentType.displayName, jsonDocType.vct) {
                    var showOptionalJsonAttributes by remember { mutableStateOf(false) }
                    val allJsonEntries = jsonAttributeStates.entries.map { it.key to it.value }
                    val activeJsonEntries = allJsonEntries.filter { (_, state) -> state.included || state.mandatory }
                    val optionalJsonEntries = allJsonEntries.filter { (_, state) -> !state.included && !state.mandatory }

                    SegmentedItemList {
                        activeJsonEntries.forEach { (path, state) ->
                            SegmentedItemContainer {
                                AttributeEditorRow(
                                    identifier = path,
                                    state = state,
                                    onStateChange = { updated -> onJsonStateUpdated(path, updated) },
                                    onOpenDatePicker = { req -> activeDatePickerRequest = req }
                                )
                            }
                        }

                        if (optionalJsonEntries.isNotEmpty() || showOptionalJsonAttributes) {
                            SegmentedItemContainer(
                                modifier = Modifier.clickable {
                                    showOptionalJsonAttributes = !showOptionalJsonAttributes
                                }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (showOptionalJsonAttributes) {
                                            stringResource(R.string.dev_add_self_signed_hide_optional_attributes)
                                        } else {
                                            stringResource(
                                                R.string.dev_add_self_signed_show_optional_attributes,
                                                optionalJsonEntries.size
                                            )
                                        },
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            if (showOptionalJsonAttributes) {
                                optionalJsonEntries.forEach { (path, state) ->
                                    SegmentedItemContainer {
                                        AttributeEditorRow(
                                            identifier = path,
                                            state = state,
                                            onStateChange = { updated -> onJsonStateUpdated(path, updated) },
                                            onOpenDatePicker = { req -> activeDatePickerRequest = req }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Front-facing camera selfie section (when supported by mdoc portrait equivalence or JSON portrait claim)
            if (supportsPortrait) {
                Text(
                    text = stringResource(R.string.dev_add_self_signed_portrait_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                val targetDescription = buildList {
                    if (mdocPeData != null) {
                        add("${mdocPeData.namespace} / ${mdocPeData.dataElementName}")
                    }
                    if (jsonPortraitClaim != null) {
                        add("SD-JWT claim '$jsonPortraitClaim'")
                    }
                }.joinToString(", ")
                Text(
                    text = stringResource(R.string.dev_add_self_signed_portrait_target_info, targetDescription),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!cameraPermissionState.isGranted) {
                    SegmentedItemList {
                        SegmentedItemContainer {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.dev_add_self_signed_camera_permission_needed),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            cameraPermissionState.launchPermissionRequest()
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.dev_add_self_signed_grant_permission))
                                }
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
                        Text(stringResource(R.string.dev_add_self_signed_take_photo))
                    }
                } else {
                    capturedImageBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(R.string.dev_add_self_signed_portrait_captured),
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
                        Text(stringResource(R.string.dev_add_self_signed_retake_photo))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val allMdocValid = mdocAttributeStates.values.all { it.isValid() }
            val allJsonValid = jsonAttributeStates.values.all { it.isValid() }
            val portraitSatisfied = !supportsPortrait || capturedBytes != null
            val canCreate = allMdocValid &&
                allJsonValid &&
                portraitSatisfied &&
                !hasKeyAuthConflict &&
                !isCreating

            Button(
                onClick = {
                    val portrait = if (supportsPortrait) {
                        capturedBytes ?: return@Button
                    } else {
                        null
                    }
                    val authDeMap = authorizedDataElements
                        .map { AuthorizedDataElementEntry(it.namespace.trim(), it.dataElement.trim()) }
                        .filter { it.namespace.isNotEmpty() && it.dataElement.isNotEmpty() }
                        .groupBy({ it.namespace }, { it.dataElement })
                        .mapValues { it.value.distinct() }
                    val authNsList = authorizedNamespaces
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()

                    val mdocDataElements = mdocAttributeStates
                        .filterValues { it.included }
                        .mapValues { (_, state) -> state.toDataItem() }
                    val jsonClaims = buildJsonClaimsFromStates(selectedDocumentType, jsonAttributeStates)

                    isCreating = true
                    coroutineScope.launch {
                        try {
                            val createdDocument = DeveloperSelfSignedDocumentIssuer.issueSelfSignedDocument(
                                documentStore = documentStore,
                                secureArea = secureArea,
                                userIssuerTrustManager = userIssuerTrustManager,
                                settingsModel = settingsModel,
                                documentType = selectedDocumentType,
                                portraitBytes = portrait,
                                mdocDataElements = mdocDataElements,
                                jsonClaims = jsonClaims,
                                authorizedNamespaces = authNsList,
                                authorizedDataElements = authDeMap
                            )
                            showToast(successToastMessage)
                            onDocumentCreated(createdDocument)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Logger.e(TAG, "Failed to create self-signed document", e)
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
                Text(stringResource(R.string.dev_add_self_signed_create_button))
            }
        }
    }

    activeDatePickerRequest?.let { pickerReq ->
        val initialDate = remember(pickerReq) {
            val raw = pickerReq.currentText.trim()
            if (pickerReq.isDateTime) {
                runCatching {
                    Instant.parse(raw).toLocalDateTime(TimeZone.UTC).date
                }.getOrDefault(today)
            } else {
                runCatching { LocalDate.parse(raw) }.getOrDefault(today)
            }
        }
        val initialMillis = initialDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

        key(pickerReq) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = initialMillis
            )

            DatePickerDialog(
                onDismissRequest = { activeDatePickerRequest = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val pickedDate = Instant.fromEpochMilliseconds(millis)
                                    .toLocalDateTime(TimeZone.UTC).date
                                pickerReq.onDatePicked(pickedDate)
                            }
                            activeDatePickerRequest = null
                        }
                    ) {
                        Text(stringResource(R.string.error_dialog_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDatePickerRequest = null }) {
                        Text(stringResource(R.string.confirmation_dialog_cancel))
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttributeEditorRow(
    identifier: String,
    state: EditableAttributeState,
    onStateChange: (EditableAttributeState) -> Unit,
    onOpenDatePicker: (DatePickerRequest) -> Unit
) {
    val attribute = state.attribute
    val labelText = "${attribute.displayName} ($identifier)"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.included,
                onCheckedChange = { checked ->
                    onStateChange(state.copy(included = checked))
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (state.included && attribute.type == DocumentAttributeType.Boolean) {
                Switch(
                    checked = state.boolValue,
                    onCheckedChange = { checked ->
                        onStateChange(state.copy(boolValue = checked))
                    }
                )
            }
        }

        if (state.included) {
            when (val type = attribute.type) {
                is DocumentAttributeType.Boolean -> {
                    // Rendered inline in the Row above
                }
                is DocumentAttributeType.String -> {
                    OutlinedTextField(
                        value = state.textValue,
                        onValueChange = { onStateChange(state.copy(textValue = it)) },
                        label = { Text(attribute.displayName) },
                        isError = !state.isValid(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }
                is DocumentAttributeType.Number -> {
                    OutlinedTextField(
                        value = state.textValue,
                        onValueChange = { onStateChange(state.copy(textValue = it)) },
                        label = { Text(attribute.displayName) },
                        isError = !state.isValid(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        )
                    )
                }
                is DocumentAttributeType.Date -> {
                    OutlinedTextField(
                        value = state.textValue,
                        onValueChange = { onStateChange(state.copy(textValue = it)) },
                        label = { Text(attribute.displayName) },
                        placeholder = { Text(stringResource(R.string.dev_add_self_signed_date_placeholder)) },
                        isError = !state.isValid(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onOpenDatePicker(
                                        DatePickerRequest(
                                            currentText = state.textValue,
                                            isDateTime = false,
                                            onDatePicked = { picked ->
                                                onStateChange(state.copy(textValue = picked.toString()))
                                            }
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = stringResource(R.string.dev_add_self_signed_date_picker_title)
                                )
                            }
                        }
                    )
                }
                is DocumentAttributeType.DateTime -> {
                    OutlinedTextField(
                        value = state.textValue,
                        onValueChange = { onStateChange(state.copy(textValue = it)) },
                        label = { Text(attribute.displayName) },
                        placeholder = { Text(stringResource(R.string.dev_add_self_signed_datetime_placeholder)) },
                        isError = !state.isValid(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onOpenDatePicker(
                                        DatePickerRequest(
                                            currentText = state.textValue,
                                            isDateTime = true,
                                            onDatePicked = { picked ->
                                                onStateChange(state.copy(textValue = "${picked}T00:00:00Z"))
                                            }
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = stringResource(R.string.dev_add_self_signed_date_picker_title)
                                )
                            }
                        }
                    )
                }
                is DocumentAttributeType.IntegerOptions -> {
                    var expanded by remember { mutableStateOf(false) }
                    val nonNullOptions = remember(type.options) {
                        type.options.filter { it.value != null }
                    }
                    val selectedOpt = nonNullOptions.find { it.value == state.intOptionValue }
                        ?: nonNullOptions.firstOrNull()

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedOpt?.displayName ?: state.intOptionValue?.toString().orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(attribute.displayName) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            nonNullOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        onStateChange(state.copy(intOptionValue = option.value))
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                is DocumentAttributeType.StringOptions -> {
                    var expanded by remember { mutableStateOf(false) }
                    var searchQuery by remember { mutableStateOf("") }
                    val nonNullOptions = remember(type.options) {
                        type.options.filter { it.value != null }
                    }
                    val selectedOpt = nonNullOptions.find { it.value == state.textValue }
                    val selectedDisplay = selectedOpt?.let { "${it.displayName} (${it.value})" }
                        ?: state.textValue

                    val filteredOptions = remember(searchQuery, nonNullOptions) {
                        if (searchQuery.isBlank()) {
                            nonNullOptions
                        } else {
                            nonNullOptions.filter {
                                it.displayName.contains(searchQuery, ignoreCase = true) ||
                                    (it.value?.contains(searchQuery, ignoreCase = true) == true)
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = {
                            expanded = it
                            if (!it) searchQuery = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedDisplay,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(attribute.displayName) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = {
                                expanded = false
                                searchQuery = ""
                            },
                            modifier = Modifier.heightIn(max = 320.dp)
                        ) {
                            if (nonNullOptions.size > 15) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text(stringResource(R.string.dev_add_self_signed_search_options)) },
                                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Outlined.Clear, contentDescription = null)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (filteredOptions.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dev_add_self_signed_no_matches)) },
                                    onClick = {},
                                    enabled = false
                                )
                            } else {
                                filteredOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text("${option.displayName} (${option.value})") },
                                        onClick = {
                                            onStateChange(state.copy(textValue = option.value!!))
                                            expanded = false
                                            searchQuery = ""
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                is DocumentAttributeType.Blob,
                is DocumentAttributeType.Picture -> {
                    OutlinedTextField(
                        value = state.textValue,
                        onValueChange = { onStateChange(state.copy(textValue = it)) },
                        label = { Text(attribute.displayName) },
                        placeholder = {
                            if (!state.isJson) {
                                Text(stringResource(R.string.dev_add_self_signed_hex_placeholder))
                            }
                        },
                        isError = !state.isValid(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                is DocumentAttributeType.ComplexType -> {
                    if (state.isJson) {
                        OutlinedTextField(
                            value = state.textValue,
                            onValueChange = { onStateChange(state.copy(textValue = it)) },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.dev_add_self_signed_json_label,
                                        attribute.displayName
                                    )
                                )
                            },
                            isError = !state.isValid(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val decodedDiag = remember(state.textValue) {
                            runCatching {
                                Cbor.toDiagnostics(Cbor.decode(state.textValue.trim().fromHex()))
                            }.getOrNull()
                        }
                        OutlinedTextField(
                            value = state.textValue,
                            onValueChange = { onStateChange(state.copy(textValue = it)) },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.dev_add_self_signed_cbor_hex_label,
                                        attribute.displayName
                                    )
                                )
                            },
                            isError = !state.isValid(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (decodedDiag != null) {
                            Text(
                                text = stringResource(
                                    R.string.dev_add_self_signed_cbor_diagnostics_prefix,
                                    decodedDiag
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
