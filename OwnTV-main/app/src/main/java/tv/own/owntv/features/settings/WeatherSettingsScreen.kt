package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.restoreAfterDialogClose
import tv.own.owntv.ui.components.roundedPanel

/**
 * Weather settings — the top-bar weather chip: show/hide, manual location override, and °C/°F.
 * (Grew out of two loose rows on the Settings root; grouped here as its own submenu.)
 */
@Composable
fun WeatherSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val enabled by vm.weatherEnabled.collectAsStateWithLifecycle()
    val location by vm.weatherLocation.collectAsStateWithLifecycle()
    val fahrenheit by vm.weatherFahrenheit.collectAsStateWithLifecycle()

    var showLocation by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val locationRowFocus = remember { FocusRequester() }
    // The opener row for the location dialog, captured when the dialog opens and consumed when it
    // closes — so focus returns to the "Custom location" row, not the "Show weather" first row.
    val scrollState = rememberScrollState()
    var savedScroll by remember { mutableIntStateOf(0) }
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    LaunchedEffect(showLocation) {
        // Single owner of dialogReturn: set it when opening, consume it (with a clear) when closing.
        // Previously onEnter also read+cleared it, racing this effect — whoever fired first won and
        // the other no-op'd, so the restore sometimes landed on the wrong row.
        if (showLocation) {
            savedScroll = scrollState.value
            dialogReturn = locationRowFocus
        } else {
            restoreAfterDialogClose(dialogReturn, scrollState, savedScroll)
            dialogReturn = null
        }
    }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            // onEnter handles ONLY genuine external (directional) entry — it targets the first row.
            // Programmatic dialog-close restore is owned by the LaunchedEffect above (onEnter does not
            // fire for programmatic requestFocus), so it must not consult dialogReturn.
            .focusProperties { onEnter = { runCatching { firstFocus.requestFocus() } } }
            .focusGroup()
            .verticalScroll(scrollState)
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Header(stringResource(R.string.settings_weather), onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel(stringResource(R.string.settings_top_bar_weather))
        Row2(
            icon = OwnTVIcon.WEATHER, title = stringResource(R.string.settings_show_weather),
            desc = stringResource(R.string.settings_show_weather_description),
            chip = if (enabled) stringResource(R.string.common_on) else stringResource(R.string.common_off), primaryChip = enabled,
            modifier = Modifier.focusRequester(firstFocus),
            onClick = { vm.setWeatherEnabled(!enabled) },
        )
        Row2(
            icon = OwnTVIcon.WEATHER, title = stringResource(R.string.settings_custom_location),
            desc = stringResource(R.string.settings_custom_location_description),
            chip = location.ifBlank { stringResource(R.string.settings_auto) }, primaryChip = false, chevron = true,
            modifier = Modifier.focusRequester(locationRowFocus),
            onClick = { showLocation = true },
        )
        Row2(
            icon = OwnTVIcon.WEATHER, title = stringResource(R.string.settings_temperature_unit),
            desc = stringResource(R.string.settings_temperature_description),
            chip = stringResource(if (fahrenheit) R.string.settings_degree_fahrenheit else R.string.settings_degree_celsius), primaryChip = true,
            onClick = { vm.setWeatherFahrenheit(!fahrenheit) },
        )
    }

    if (showLocation) {
        TextInputDialog(
            title = stringResource(R.string.settings_custom_location),
            initial = location,
            label = stringResource(R.string.settings_city_latlon),
            hint = stringResource(R.string.settings_location_hint),
            onConfirm = { vm.setWeatherLocation(it); showLocation = false },
            onDismiss = { showLocation = false },
        )
    }
}
