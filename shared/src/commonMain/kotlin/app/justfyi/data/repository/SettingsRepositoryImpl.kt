package app.justfyi.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.justfyi.data.local.SettingsQueries
import app.justfyi.domain.repository.SettingsRepository
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Multiplatform implementation of SettingsRepository.
 * Pure local storage using SQLDelight, no cloud sync.
 *
 * This implementation is platform-agnostic and works on both
 * Android and iOS.
 */
@Inject
class SettingsRepositoryImpl(
    private val settingsQueries: SettingsQueries,
    private val dispatchers: AppCoroutineDispatchers,
) : SettingsRepository {
    override suspend fun getSetting(key: String): String? =
        withContext(dispatchers.io) {
            settingsQueries.getSettingByKey(key).executeAsOneOrNull()
        }

    override suspend fun setSetting(
        key: String,
        value: String,
    ): Unit =
        withContext(dispatchers.io) {
            settingsQueries.insertOrReplaceSetting(key, value)
            Unit
        }

    override suspend fun getLanguage(): String? =
        withContext(dispatchers.io) {
            settingsQueries
                .getSettingByKey(SettingsRepository.KEY_LANGUAGE_OVERRIDE)
                .executeAsOneOrNull()
        }

    override suspend fun setLanguage(languageCode: String): Unit =
        withContext(dispatchers.io) {
            settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_LANGUAGE_OVERRIDE,
                languageCode,
            )
            Unit
        }

    override suspend fun getTheme(): String =
        withContext(dispatchers.io) {
            settingsQueries
                .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                .executeAsOneOrNull() ?: DEFAULT_THEME
        }

    override suspend fun setTheme(theme: String): Unit =
        withContext(dispatchers.io) {
            settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                theme,
            )
            Unit
        }

    override fun observeSetting(key: String): Flow<String?> =
        settingsQueries
            .getSettingByKey(key)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)

    override suspend fun isPrivacyPolicyAccepted(): Boolean =
        withContext(dispatchers.io) {
            val value =
                settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_PRIVACY_POLICY_ACCEPTED)
                    .executeAsOneOrNull()
            value == TRUE_VALUE
        }

    override suspend fun setPrivacyPolicyAccepted(accepted: Boolean): Unit =
        withContext(dispatchers.io) {
            settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_PRIVACY_POLICY_ACCEPTED,
                if (accepted) TRUE_VALUE else FALSE_VALUE,
            )
            Unit
        }

    override suspend fun isTermsAccepted(): Boolean =
        withContext(dispatchers.io) {
            val value =
                settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_TERMS_ACCEPTED)
                    .executeAsOneOrNull()
            value == TRUE_VALUE
        }

    override suspend fun setTermsAccepted(accepted: Boolean): Unit =
        withContext(dispatchers.io) {
            settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_TERMS_ACCEPTED,
                if (accepted) TRUE_VALUE else FALSE_VALUE,
            )
            Unit
        }

    override suspend fun isOnboardingComplete(): Boolean =
        withContext(dispatchers.io) {
            val value =
                settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
                    .executeAsOneOrNull()
            value == TRUE_VALUE
        }

    override suspend fun setOnboardingComplete(complete: Boolean): Unit =
        withContext(dispatchers.io) {
            settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_ONBOARDING_COMPLETE,
                if (complete) TRUE_VALUE else FALSE_VALUE,
            )
            Unit
        }

    override fun observeOnboardingComplete(): Flow<Boolean> =
        settingsQueries
            .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)
            .map { it == TRUE_VALUE }

    override suspend fun deleteAllSettings(): Unit =
        withContext(dispatchers.io) {
            settingsQueries.deleteAllSettings()
            Unit
        }

    companion object {
        private const val TRUE_VALUE = "true"
        private const val FALSE_VALUE = "false"
        private const val DEFAULT_THEME = "system"
    }
}
