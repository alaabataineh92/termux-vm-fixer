package io.github.alaabataineh92.termuxvmfixer.core.device

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.alaabataineh92.termuxvmfixer.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore by preferencesDataStore(name = "settings")

@Singleton
class ThemePreference @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { preferences ->
        preferences[KEY]?.let { stored ->
            ThemeMode.entries.firstOrNull { it.name == stored }
        } ?: ThemeMode.SYSTEM
    }

    suspend fun set(themeMode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[KEY] = themeMode.name
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("theme_mode")
    }
}
