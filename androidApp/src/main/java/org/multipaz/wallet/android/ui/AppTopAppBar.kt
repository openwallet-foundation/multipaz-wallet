package org.multipaz.wallet.android.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Applies Haze progressive blur and surface tint to an app bar.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.hazeTopAppBar(hazeState: HazeState?): Modifier {
    if (hazeState == null) return this
    return this.hazeEffect(
        state = hazeState,
        style = HazeMaterials.thin(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        progressive = HazeProgressive.verticalGradient(
            startIntensity = 1f,
            endIntensity = 0f
        )
    }
}

/**
 * Standard back navigation button for top app bars.
 */
@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null
        )
    }
}

/**
 * Standard [TopAppBar] with transparent background and progressive backdrop blur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        modifier = modifier.hazeTopAppBar(hazeState)
    )
}

/**
 * Standard [MediumTopAppBar] with transparent background and progressive backdrop blur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMediumTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    MediumTopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        modifier = modifier.hazeTopAppBar(hazeState)
    )
}

/**
 * Standard [CenterAlignedTopAppBar] with transparent background and progressive backdrop blur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCenterAlignedTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    CenterAlignedTopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        modifier = modifier.hazeTopAppBar(hazeState)
    )
}
