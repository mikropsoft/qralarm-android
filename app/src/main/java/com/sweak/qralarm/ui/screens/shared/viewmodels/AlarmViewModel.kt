package com.sweak.qralarm.ui.screens.shared.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.sweak.qralarm.alarm.QRAlarmManager
import com.sweak.qralarm.data.DataStoreManager
import com.sweak.qralarm.ui.screens.home.HomeUiState
import com.sweak.qralarm.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val qrAlarmManager: QRAlarmManager
) : ViewModel() {

    val homeUiState: MutableState<HomeUiState> = runBlocking {
        val alarmTimeInMillis =
            if (dataStoreManager.getBoolean(DataStoreManager.ALARM_SNOOZED).first()) {
                dataStoreManager.getLong(DataStoreManager.SNOOZE_ALARM_TIME_IN_MILLIS).first()
            } else {
                dataStoreManager.getLong(DataStoreManager.ALARM_TIME_IN_MILLIS).first()
            }
        val timeFormat = getTimeFormat()

        mutableStateOf(
            HomeUiState(
                alarmTimeFormat = timeFormat,
                alarmHourOfDay = getAlarmHourOfDay(alarmTimeInMillis),
                alarmMinute = getAlarmMinute(alarmTimeInMillis),
                alarmSet = dataStoreManager.getBoolean(DataStoreManager.ALARM_SET).first(),
                alarmServiceRunning =
                dataStoreManager.getBoolean(DataStoreManager.ALARM_SERVICE_RUNNING).first(),
                snoozeAvailable = false,
                showAlarmPermissionDialog = false,
                showCameraPermissionDialog = false,
                showCameraPermissionRevokedDialog = false,
                showNotificationsPermissionDialog = false,
                showNotificationsPermissionRevokedDialog = false,
                snackbarHostState = SnackbarHostState()
            )
        )
    }

    init {
        viewModelScope.launch {
            dataStoreManager.getBoolean(DataStoreManager.ALARM_SERVICE_RUNNING).collect {
                homeUiState.value = homeUiState.value.copy(alarmServiceRunning = it)
            }
        }
        viewModelScope.launch {
            dataStoreManager.getInt(DataStoreManager.SNOOZE_AVAILABLE_COUNT).collect {
                homeUiState.value = homeUiState.value.copy(snoozeAvailable = it > 0)
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    fun handleStartOrStopButtonClick(
        navController: NavHostController,
        cameraPermissionState: PermissionState,
        notificationsPermissionState: PermissionState,
        composableScope: CoroutineScope,
        snackbarInitializer: suspend (Pair<Int, Int>) -> SnackbarResult
    ) {
        if (!cameraPermissionState.hasPermission) {
            when {
                !cameraPermissionState.permissionRequested ||
                        cameraPermissionState.shouldShowRationale -> {
                    homeUiState.value = homeUiState.value.copy(showCameraPermissionDialog = true)
                    return
                }
                !cameraPermissionState.shouldShowRationale -> {
                    homeUiState.value =
                        homeUiState.value.copy(showCameraPermissionRevokedDialog = true)
                    return
                }
            }
        }

        if (!notificationsPermissionState.hasPermission) {
            when {
                !notificationsPermissionState.permissionRequested ||
                        notificationsPermissionState.shouldShowRationale -> {
                    homeUiState.value = homeUiState.value.copy(
                        showNotificationsPermissionDialog = true
                    )
                    return
                }
                !notificationsPermissionState.shouldShowRationale -> {
                    homeUiState.value =
                        homeUiState.value.copy(showNotificationsPermissionRevokedDialog = true)
                    return
                }
            }
        }

        val alarmSet = homeUiState.value.alarmSet

        if (!alarmSet) {
            try {
                qrAlarmManager.setAlarm(
                    getAlarmTimeInMillis(
                        homeUiState.value.alarmHourOfDay,
                        homeUiState.value.alarmMinute
                    ),
                    ALARM_TYPE_NORMAL
                )

                val alarmTimeInMillis = getAlarmTimeInMillis(
                    homeUiState.value.alarmHourOfDay,
                    homeUiState.value.alarmMinute
                )

                viewModelScope.launch {
                    dataStoreManager.apply {
                        putBoolean(DataStoreManager.ALARM_SET, true)
                        putBoolean(DataStoreManager.ALARM_SNOOZED, false)
                        homeUiState.value = homeUiState.value.copy(alarmSet = true)

                        putLong(DataStoreManager.ALARM_TIME_IN_MILLIS, alarmTimeInMillis)

                        putInt(
                            DataStoreManager.SNOOZE_AVAILABLE_COUNT,
                            getInt(DataStoreManager.SNOOZE_MAX_COUNT).first()
                        )
                    }
                }

                composableScope.launch {
                    val snackbarResult = snackbarInitializer(
                        getHoursAndMinutesUntilTimePair(alarmTimeInMillis)
                    )

                    when (snackbarResult) {
                        SnackbarResult.ActionPerformed -> {
                            delay(500)
                            stopAlarm()
                        }
                        SnackbarResult.Dismissed -> { /* no-op */ }
                    }
                }
            } catch (exception: SecurityException) {
                homeUiState.value = homeUiState.value.copy(showAlarmPermissionDialog = true)
                return
            }
        } else {
            navController.navigate(Screen.ScannerScreen.withArguments(SCAN_MODE_DISMISS_ALARM))
        }
    }

    fun stopAlarm(): Job {
        qrAlarmManager.cancelAlarm()

        val stopAlarmJob = viewModelScope.launch {
            dataStoreManager.apply {
                putBoolean(DataStoreManager.ALARM_SET, false)
                putBoolean(DataStoreManager.ALARM_SNOOZED, false)

                val originalAlarmTimeInMillis =
                    getLong(DataStoreManager.ALARM_TIME_IN_MILLIS).first()

                homeUiState.value = homeUiState.value.copy(
                    alarmSet = false,
                    alarmHourOfDay = getAlarmHourOfDay(originalAlarmTimeInMillis),
                    alarmMinute = getAlarmMinute(originalAlarmTimeInMillis)
                )
            }
        }

        return stopAlarmJob
    }

    fun handleSnoozeButtonClick(
        snoozeSideEffect: () -> Unit
    ) {
        qrAlarmManager.cancelAlarm()

        viewModelScope.launch {
            dataStoreManager.apply {
                val snoozeAlarmTimeInMillis = getSnoozeAlarmTimeInMillis(
                    getInt(DataStoreManager.SNOOZE_DURATION_MINUTES).first()
                )

                try {
                    qrAlarmManager.setAlarm(snoozeAlarmTimeInMillis, ALARM_TYPE_SNOOZE)
                } catch (exception: SecurityException) {
                    homeUiState.value = homeUiState.value.copy(showAlarmPermissionDialog = true)
                    return@launch
                }

                putBoolean(DataStoreManager.ALARM_SET, true)
                putBoolean(DataStoreManager.ALARM_SNOOZED, true)
                homeUiState.value = homeUiState.value.copy(alarmSet = true)

                val alarmHour = getAlarmHourOfDay(snoozeAlarmTimeInMillis)
                val alarmMinute = getAlarmMinute(snoozeAlarmTimeInMillis)

                putLong(
                    DataStoreManager.SNOOZE_ALARM_TIME_IN_MILLIS,
                    getAlarmTimeInMillis(alarmHour, alarmMinute)
                )

                homeUiState.value = homeUiState.value.copy(
                    alarmHourOfDay = alarmHour,
                    alarmMinute = alarmMinute
                )

                val availableSnoozes = getInt(DataStoreManager.SNOOZE_AVAILABLE_COUNT).first()
                putInt(DataStoreManager.SNOOZE_AVAILABLE_COUNT, availableSnoozes - 1)
            }

            snoozeSideEffect.invoke()
        }
    }

    private suspend fun getTimeFormat(): TimeFormat {
        val timeFormat = dataStoreManager.getInt(DataStoreManager.ALARM_TIME_FORMAT).first()

        return TimeFormat.fromInt(timeFormat) ?: TimeFormat.MILITARY
    }

    fun getDismissCode(): String =
        runBlocking {
            dataStoreManager.getString(DataStoreManager.DISMISS_ALARM_CODE).first()
        }
}