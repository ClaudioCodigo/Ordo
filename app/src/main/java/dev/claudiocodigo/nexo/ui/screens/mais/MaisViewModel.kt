package dev.claudiocodigo.nexo.ui.screens.mais

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaisViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val nextcloudUrl: Flow<String?> = userPreferencesRepository.nextcloudUrl

    fun updateNextcloudUrl(url: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateNextcloudUrl(url)
        }
    }
}
