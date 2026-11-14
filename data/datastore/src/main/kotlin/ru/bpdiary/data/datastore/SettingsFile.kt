package ru.bpdiary.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore

/** One process-wide instance; the file is `settings.preferences_pb`. A corrupted file is replaced by the defaults. */
val Context.settingsPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

fun createSettingsDataStore(context: Context): SettingsDataStore = SettingsDataStore(context.settingsPreferences)
