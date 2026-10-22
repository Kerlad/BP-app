package ru.bpdiary.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fake `DataStore<Preferences>` kept in memory.
 *
 * The real file store cannot be used in JVM tests on Windows: `File.renameTo` does not replace an existing
 * file there, so every second write fails. The library's own file handling is not what these tests check.
 */
class InMemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val lock = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        lock.withLock {
            transform(state.value).also { state.value = it }
        }
}
