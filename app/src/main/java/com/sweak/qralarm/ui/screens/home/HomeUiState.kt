package com.sweak.qralarm.ui.screens.home

import androidx.compose.material.SnackbarHostState
import com.sweak.qralarm.util.TimeFormat

data class HomeUiState(
    var alarmTimeFormat: TimeFormat,
    var alarmHourOfDay: Int,
    var alarmMinute: Int,
    var alarmSet: Boolean,
    var alarmServiceRunning: Boolean,
    var snoozeAvailable: Boolean,
    var showAlarmPermissionDialog: Boolean,
    var showCameraPermissionDialog: Boolean,
    var showCameraPermissionRevokedDialog: Boolean,
    var showNotificationsPermissionDialog: Boolean,
    var showNotificationsPermissionRevokedDialog: Boolean,
    var snackbarHostState: SnackbarHostState
)