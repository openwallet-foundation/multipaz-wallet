package org.multipaz.wallet.android.ui.provisioning

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import org.multipaz.compose.cards.VerticalCardList
import org.multipaz.compose.document.DocumentModel
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppTopAppBar
import org.multipaz.wallet.android.ui.JustAdded
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisioningCompleteScreen(
    documentModel: DocumentModel,
    documentId: String,
    autoFinish: Boolean = true,
    onComplete: () -> Unit,
) {
    val hazeState = remember { HazeState() }
    val documentInfo = documentModel.documentInfos.collectAsState().value.find {
        it.document.identifier == documentId
    }

    BackHandler {
        onComplete()
    }

    if (autoFinish) {
        LaunchedEffect(Unit) {
            delay(3.seconds)
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onComplete) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.provisioning_progress_cancel_description)
                        )
                    }
                },
                hazeState = hazeState
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current)
                )
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            VerticalCardList(
                modifier = Modifier.fillMaxSize(),
                cardInfos = listOfNotNull(documentInfo),
                focusedCard = documentInfo,
                allowCardReordering = false,
                showStackWhileFocused = false,
                cardMaxHeight = Dp.Unspecified,
                paddingTop = innerPadding.calculateTopPadding(),
                paddingBottom = 136.dp,
                animateListTransitions = false,
                showCardInfo = {
                    JustAdded()
                },
                emptyContent = {},
                onCardFocused = {},
                onCardFocusedTapped = { onComplete() },
                onCardFocusedStackTapped = { onComplete() }
            )
        }
    }
}
