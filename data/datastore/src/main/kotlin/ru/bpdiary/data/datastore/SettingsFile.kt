package ru.bpdiary.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** One process-wide instance; the file is `settings.preferences_pb`. */
val Context.settingsPreferences: DataStore<Preferences> by preferencesDataStore(name = "settings")

fun createSettingsDataStore(context: Context): SettingsDataStore = SettingsDataStore(context.settingsPreferences)
