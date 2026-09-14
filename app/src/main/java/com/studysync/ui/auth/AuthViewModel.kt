package com.studysync.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studysync.data.AuthMode
import com.studysync.data.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val mode: AuthMode = AuthMode.LOGIN,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Shown after password reset email is requested (Firebase may take a minute to deliver). */
    val infoMessage: String? = null,
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false
)

class AuthViewModel(private val repository: StudyRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun onEmailChanged(value: String) = _state.update { it.copy(email = value, infoMessage = null) }

    fun onPasswordChanged(value: String) = _state.update { it.copy(password = value) }

    fun onConfirmPasswordChanged(value: String) = _state.update { it.copy(confirmPassword = value) }

    fun onPasswordVisibilityToggle() =
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onConfirmPasswordVisibilityToggle() =
        _state.update { it.copy(confirmPasswordVisible = !it.confirmPasswordVisible) }

    fun onFirstNameChanged(value: String) = _state.update { it.copy(firstName = value) }

    fun onLastNameChanged(value: String) = _state.update { it.copy(lastName = value) }

    fun onPhoneChanged(value: String) = _state.update { it.copy(phoneNumber = value) }

    fun onDisplayNameChanged(value: String) = _state.update { it.copy(displayName = value) }

    fun onToggleMode() = _state.update {
        it.copy(
            mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN,
            error = null,
            infoMessage = null
        )
    }

    fun onForgotPassword() {
        val email = _state.value.email.trim()
        if (email.isBlank()) {
            _state.update { it.copy(error = "Enter your email, then tap Forgot password") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, infoMessage = null) }
            repository.sendPasswordResetEmail(email).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            infoMessage = "If an account exists for that email, you’ll get a reset link shortly. It can take a minute—check spam too."
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.message, infoMessage = null)
                    }
                }
            )
        }
    }

    fun onSubmit() {
        val current = _state.value
        val isRegister = current.mode == AuthMode.REGISTER

        if (current.email.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "Email and password required") }
            return
        }

        if (isRegister) {
            if (current.firstName.isBlank() || current.lastName.isBlank()) {
                _state.update { it.copy(error = "Name and surname required") }
                return
            }
            if (current.phoneNumber.isBlank()) {
                _state.update { it.copy(error = "Phone number required") }
                return
            }
            if (current.password != current.confirmPassword) {
                _state.update { it.copy(error = "Passwords do not match") }
                return
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, infoMessage = null) }
            val result = when (current.mode) {
                AuthMode.LOGIN -> repository.login(current.email.trim(), current.password)
                AuthMode.REGISTER -> repository.register(
                    email = current.email.trim(),
                    password = current.password,
                    displayName = buildString {
                        if (current.firstName.isNotBlank()) append(current.firstName.trim())
                        if (current.lastName.isNotBlank()) {
                            if (isNotEmpty()) append(" ")
                            append(current.lastName.trim())
                        }
                        if (isEmpty()) append(current.displayName.ifBlank { current.email.substringBefore("@") })
                    }
                )
            }

            result.fold(
                onSuccess = { _state.update { it.copy(isLoading = false, error = null) } },
                onFailure = { throwable ->
                    _state.update { state ->
                        state.copy(isLoading = false, error = throwable.message)
                    }
                }
            )
        }
    }

    /** Completes Firebase sign-in with a Google ID token. If [idToken] is null, [errorMessage] is shown when non-null. */
    fun onGoogleSignInResult(idToken: String?, errorMessage: String? = null) {
        if (idToken == null) {
            _state.update {
                it.copy(isLoading = false, error = errorMessage)
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, infoMessage = null) }
            repository.signInWithGoogle(idToken).fold(
                onSuccess = { _state.update { it.copy(isLoading = false, error = null) } },
                onFailure = { e ->
                    _state.update { s -> s.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    companion object {
        fun factory(repository: StudyRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(repository) as T
                }
            }
    }
}
