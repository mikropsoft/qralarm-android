package com.sweak.qralarm.ui.screens.settings

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.sweak.qralarm.R
import com.sweak.qralarm.data.DataStoreManager
import com.sweak.qralarm.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.*
import javax.inject.Inject

@ExperimentalPermissionsApi
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val resourceProvider: ResourceProvider,
    private val mediaPlayer: MediaPlayer
) : ViewModel() {

    val settingsUiState: MutableState<SettingsUiState> = runBlocking {
        dataStoreManager.let {
            mutableStateOf(
                SettingsUiState(
                    availableAlarmSounds = AVAILABLE_ALARM_SOUNDS.map { alarmSound ->
                        resourceProvider.getString(alarmSound.nameResourceId)
                    },
                    selectedAlarmSoundIndex = AVAILABLE_ALARM_SOUNDS.indexOf(
                        AlarmSound.fromInt(it.getInt(DataStoreManager.ALARM_SOUND).first())
                    ),
                    selectedSnoozeDurationIndex = AVAILABLE_SNOOZE_DURATIONS.indexOf(
                        it.getInt(DataStoreManager.SNOOZE_DURATION_MINUTES).first()
                    ),
                    selectedSnoozeMaxCountIndex = AVAILABLE_SNOOZE_MAX_COUNTS.indexOf(
                        it.getInt(DataStoreManager.SNOOZE_MAX_COUNT).first()
                    ),
                    dismissAlarmCode = it.getString(DataStoreManager.DISMISS_ALARM_CODE).first()
                )
            )
        }
    }

    fun playOrStopAlarmPreview(context: Context) {
        if (!settingsUiState.value.alarmPreviewPlaying) {
            mediaPlayer.apply {
                reset()
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                try {
                    setDataSource(
                        context,
                        getPreferredAlarmSoundUri(context.packageName)
                    )
                } catch (ioException: IOException) {
                    return@apply
                }
                isLooping = false
                setOnCompletionListener {
                    stopMediaPlayer()
                }
                prepare()
                start()
            }

            settingsUiState.value = settingsUiState.value.copy(alarmPreviewPlaying = true)
        } else {
            stopMediaPlayer()
        }
    }

    private fun getPreferredAlarmSoundUri(packageName: String): Uri {
        return if (settingsUiState.value.selectedAlarmSoundIndex == AlarmSound.LOCAL_SOUND.ordinal) {
            runBlocking {
                Uri.parse(
                    dataStoreManager.getString(DataStoreManager.LOCAL_ALARM_SOUND_URI).first()
                )
            }
        } else {
            AlarmSound.fromInt(settingsUiState.value.selectedAlarmSoundIndex).let {
                Uri.parse(
                    "android.resource://"
                            + packageName
                            + "/"
                            + (it?.resourceId ?: AlarmSound.GENTLE_GUITAR.resourceId)
                )
            }
        }
    }

    fun updateAlarmSoundSelection(newIndex: Int) {
        val newSelectedAlarmSound = AVAILABLE_ALARM_SOUNDS[newIndex]

        settingsUiState.value = settingsUiState.value.copy(
            selectedAlarmSoundIndex = newIndex
        )

        viewModelScope.launch {
            dataStoreManager.putInt(
                DataStoreManager.ALARM_SOUND,
                newSelectedAlarmSound.ordinal
            )
        }
    }

    fun updateLocalAlarmSoundSelection(uri: Uri?, context: Context) {
        uri?.let {
            viewModelScope.launch {
                val savedLocalAlarmSoundUri = try {
                    copyUriContentToLocalStorage(uri, context)
                } catch (ioException: IOException) {
                    Toast.makeText(
                        context,
                        resourceProvider.getString(R.string.not_saved_local_sound),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                dataStoreManager.apply {
                    putString(
                        DataStoreManager.LOCAL_ALARM_SOUND_URI,
                        savedLocalAlarmSoundUri.toString()
                    )
                    putInt(
                        DataStoreManager.ALARM_SOUND,
                        AlarmSound.LOCAL_SOUND.ordinal
                    )
                }

                settingsUiState.value = settingsUiState.value.copy(
                    selectedAlarmSoundIndex = AlarmSound.LOCAL_SOUND.ordinal
                )

                Toast.makeText(
                    context,
                    resourceProvider.getString(R.string.saved_local_sound),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun copyUriContentToLocalStorage(uri: Uri, context: Context): Uri {
        val fileName = "qralarm_user_selected_alarm_sound"
        val file = File(context.filesDir, fileName)

        file.createNewFile()

        FileOutputStream(file).use { outputStream ->
            context.contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) {
                    throw IOException()
                }

                copyStream(inputStream, outputStream)
                outputStream.flush()
            }
        }

        return Uri.fromFile(file)
    }

    private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int

        while (inputStream.read(buffer).also { read = it } != -1) {
            outputStream.write(buffer, 0, read)
        }
    }

    fun isLocalSoundAlarmChosen(index: Int): Boolean {
        return AVAILABLE_ALARM_SOUNDS[index].ordinal == AlarmSound.LOCAL_SOUND.ordinal
    }

    fun updateSnoozeDurationSelection(newIndex: Int) {
        val newSelectedSnoozeDuration = AVAILABLE_SNOOZE_DURATIONS[newIndex]

        settingsUiState.value = settingsUiState.value.copy(
            selectedSnoozeDurationIndex = newIndex
        )

        viewModelScope.launch {
            dataStoreManager.putInt(
                DataStoreManager.SNOOZE_DURATION_MINUTES,
                newSelectedSnoozeDuration
            )
        }
    }

    fun updateSnoozeMaxCountSelection(newIndex: Int) {
        val newSelectedSnoozeMaxCount = AVAILABLE_SNOOZE_MAX_COUNTS[newIndex]

        settingsUiState.value = settingsUiState.value.copy(
            selectedSnoozeMaxCountIndex = newIndex
        )

        viewModelScope.launch {
            dataStoreManager.putInt(
                DataStoreManager.SNOOZE_MAX_COUNT,
                newSelectedSnoozeMaxCount
            )
        }
    }

    fun handleDefaultCodeDownloadButton(
        context: Context,
        storagePermissionState: PermissionState
    ) {
        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val storageWritePermissionGranted = storagePermissionState.hasPermission || minSdk29

        if (!storageWritePermissionGranted) {
            when {
                !storagePermissionState.permissionRequested ||
                        storagePermissionState.shouldShowRationale -> {
                    settingsUiState.value =
                        settingsUiState.value.copy(showStoragePermissionDialog = true)
                    return
                }
                !storagePermissionState.shouldShowRationale -> {
                    settingsUiState.value =
                        settingsUiState.value.copy(showStoragePermissionRevokedDialog = true)
                    return
                }
            }
        }

        val qrCodeImageBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.qr_code)

        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "QRAlarmCode.jpg")
            put(MediaStore.Images.Media.DISPLAY_NAME, "QRAlarmCode.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, qrCodeImageBitmap.width)
            put(MediaStore.Images.Media.HEIGHT, qrCodeImageBitmap.height)
            put(MediaStore.Images.Media.DATE_TAKEN, currentTimeInMillis())
            put(MediaStore.Images.Media.DATE_ADDED, currentTimeInMillis())
        }

        try {
            with(context.contentResolver) {
                insert(imageCollection, contentValues)?.also { uri ->
                    openOutputStream(uri).use { outputStream ->
                        if (
                            !qrCodeImageBitmap.compress(
                                Bitmap.CompressFormat.JPEG,
                                95,
                                outputStream
                            )
                        ) {
                            throw IOException("Couldn't save the QRCode Bitmap file!")
                        }
                    }
                } ?: throw IOException("Couldn't create a MediaStore entry!")
            }

            Toast.makeText(
                context,
                resourceProvider.getString(R.string.saved_default_qrcode),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: IOException) {
            e.printStackTrace()

            Toast.makeText(
                context,
                resourceProvider.getString(R.string.not_saved_default_qrcode),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun setCustomQRCode(code: String) {
        viewModelScope.launch {
            dataStoreManager.putString(DataStoreManager.DISMISS_ALARM_CODE, code)
            settingsUiState.value = settingsUiState.value.copy(
                showDismissCodeAddedDialog = true,
                dismissAlarmCode = code
            )
        }
    }

    fun handleScanCustomDismissCodeButton(
        navController: NavHostController,
        cameraPermissionState: PermissionState
    ) {
        if (!cameraPermissionState.hasPermission) {
            when {
                !cameraPermissionState.permissionRequested ||
                        cameraPermissionState.shouldShowRationale -> {
                    settingsUiState.value =
                        settingsUiState.value.copy(showCameraPermissionDialog = true)
                    return
                }
                !cameraPermissionState.shouldShowRationale -> {
                    settingsUiState.value =
                        settingsUiState.value.copy(showCameraPermissionRevokedDialog = true)
                    return
                }
            }
        }

        navController.navigate(
            Screen.ScannerScreen.withArguments(SCAN_MODE_SET_CUSTOM_CODE)
        )
    }

    fun stopMediaPlayer() {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
        } catch (exception: IllegalStateException) {
            Log.e("SettingsViewModel", "mediaPlayer was not initialized! Cannot stop it...")
        }

        settingsUiState.value = settingsUiState.value.copy(alarmPreviewPlaying = false)
    }
}