package org.multipaz.wallet.android

import org.multipaz.context.applicationContext
import org.multipaz.eventlogger.Event
import org.multipaz.eventlogger.EventPresentment
import org.multipaz.eventlogger.EventPresentmentDigitalCredentialsMdocApi
import org.multipaz.eventlogger.EventPresentmentDigitalCredentialsOpenID4VP
import org.multipaz.eventlogger.EventPresentmentIso18013AnnexA
import org.multipaz.eventlogger.EventPresentmentIso18013Proximity
import org.multipaz.eventlogger.EventPresentmentUriSchemeOpenID4VP

// Returns true iff the event is for the given document
fun Event.isForDocumentId(documentId: String): Boolean {
    if (this is EventPresentment) {
        presentmentData.requestedDocuments.forEach {
            if (it.documentId == documentId) {
                return true
            }
        }
    }
    return false
}

fun EventPresentment.getSharingType(): String {
    val context = applicationContext
    val origin = when (this) {
        is EventPresentmentIso18013Proximity -> {
            return context.getString(R.string.event_ext_event_type_shared_in_person_text)
        }
        is EventPresentmentDigitalCredentialsMdocApi -> origin
        is EventPresentmentDigitalCredentialsOpenID4VP -> origin
        is EventPresentmentIso18013AnnexA -> origin
        is EventPresentmentUriSchemeOpenID4VP -> origin
    }
    if (origin != null) {
        if (origin.isNotEmpty() && (origin.startsWith("http://") || origin.startsWith("https://"))) {
            return context.getString(R.string.event_ext_event_type_shared_with_website_text)
        } else if (origin.isNotEmpty()) {
            return context.getString(R.string.event_ext_event_type_shared_with_app_text)
        } else {
            return context.getString(R.string.event_ext_event_type_shared_with_website_text)
        }
    }
    return context.getString(R.string.event_ext_event_type_shared_with_website_text)
}