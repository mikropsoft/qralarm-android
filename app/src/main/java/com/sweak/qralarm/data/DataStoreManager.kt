package com.sweak.qralarm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

class DataStoreManager(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("dataStore")

    suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    suspend fun putString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    suspend fun putLong(key: Preferences.Key<Long>, value: Long) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    fun getBoolean(key: Preferences.Key<Boolean>) =
        context.dataStore.data.map { preferences ->
            preferences[key] ?: true
        }

    fun getString(key: Preferences.Key<String>) =
        context.dataStore.data.map { preferences ->
            preferences[key] ?: "null"
        }

    fun getLong(key: Preferences.Key<Long>) =
        context.dataStore.data.map { preferences ->
            preferences[key] ?: 0
        }

    companion object {
        val FIRST_LAUNCH = booleanPreferencesKey("firstLaunch")
        val ALARM_SET = booleanPreferencesKey("alarmSet")
        val ALARM_SERVICE_RUNNING = booleanPreferencesKey("alarmServiceRunning")
        val ALARM_TIME_IN_MILLIS = longPreferencesKey("alarmTimeInMillis")
        val SNOOZE_ALARM_TIME_IN_MILLIS = longPreferencesKey("snoozeAlarmTimeInMillis")
        val ALARM_TIME_FORMAT = stringPreferencesKey("alarmTimeFormat")
    }
}