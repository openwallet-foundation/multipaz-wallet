package org.multipaz.wallet.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.multipaz.wallet.client.verification.AgeOverQuery
import org.multipaz.wallet.client.verification.DrivingPrivilegesQuery
import org.multipaz.wallet.client.verification.IdentificationQuery
import org.multipaz.wallet.client.verification.Query
import org.multipaz.wallet.client.verification.SimpleMdocQuery

@Composable
fun Query.getDisplayName(): String = when (this) {
    is AgeOverQuery -> {
        stringResource(R.string.reader_query_age_over, ageOver)
    }
    is IdentificationQuery -> {
        if (requestStreetAddress) {
            stringResource(R.string.reader_query_identification_and_address)
        } else {
            stringResource(R.string.reader_query_identification)
        }
    }
    is DrivingPrivilegesQuery -> {
        stringResource(R.string.reader_query_driving_privileges)
    }
    is SimpleMdocQuery -> name
}

@Composable
fun Query.getDescription(): String = when (this) {
    is AgeOverQuery -> {
        stringResource(R.string.reader_query_age_over_description, ageOver)
    }
    is IdentificationQuery -> {
        if (requestStreetAddress) {
            stringResource(R.string.reader_query_identification_and_address_description)
        } else {
            stringResource(R.string.reader_query_identification_description)
        }
    }
    is DrivingPrivilegesQuery -> {
        stringResource(R.string.reader_query_driving_privileges_description)
    }
    is SimpleMdocQuery -> description
}