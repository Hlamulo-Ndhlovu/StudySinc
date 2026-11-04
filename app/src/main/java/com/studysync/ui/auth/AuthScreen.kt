package com.studysync.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.studysync.R
import com.studysync.data.AuthMode
import com.studysync.ui.common.SectionLabel
import com.studysync.ui.common.StudySyncHeroBanner

@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    onGoogleClick: () -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onConfirmPasswordVisibilityToggle: () -> Unit,
    onForgotPassword: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        StudySyncHeroBanner(
            title = stringResource(
                if (state.mode == AuthMode.LOGIN) R.string.auth_welcome_back
                else R.string.auth_create_space
            ),
            subtitle = stringResource(R.string.auth_hero_subtitle)
        )
        Text(
            text = stringResource(R.string.auth_acceptable_use_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onGoogleClick,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.auth_continue_google))
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.auth_or_email))
        Spacer(modifier = Modifier.height(4.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.auth_email)) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.mode == AuthMode.REGISTER) {
                    OutlinedTextField(
                        value = state.firstName,
                        onValueChange = onFirstNameChange,
                        label = { Text(stringResource(R.string.auth_first_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.lastName,
                        onValueChange = onLastNameChange,
                        label = { Text(stringResource(R.string.auth_surname)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.phoneNumber,
                        onValueChange = onPhoneChange,
                        label = { Text(stringResource(R.string.auth_phone)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = onDisplayNameChange,
                        label = { Text(stringResource(R.string.auth_display_name_optional)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.auth_password)) },
                    visualTransformation = if (state.passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = onPasswordVisibilityToggle) {
                            Icon(
                                imageVector = if (state.passwordVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = stringResource(
                                    if (state.passwordVisible) R.string.auth_hide_password
                                    else R.string.auth_show_password
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.auth_password_encrypted_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.mode == AuthMode.LOGIN) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onForgotPassword, enabled = !state.isLoading) {
                            Text(stringResource(R.string.auth_forgot_password))
                        }
                    }
                }
                if (state.mode == AuthMode.REGISTER) {
                    OutlinedTextField(
                        value = state.confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        label = { Text(stringResource(R.string.auth_confirm_password)) },
                        visualTransformation = if (state.confirmPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = onConfirmPasswordVisibilityToggle) {
                                Icon(
                                    imageVector = if (state.confirmPasswordVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (state.confirmPasswordVisible) R.string.auth_hide_password
                                        else R.string.auth_show_password
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (state.infoMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.infoMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stringResource(
                        if (state.mode == AuthMode.LOGIN) R.string.auth_log_in
                        else R.string.auth_create_account
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onToggleMode,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = stringResource(
                    if (state.mode == AuthMode.LOGIN) R.string.auth_need_account
                    else R.string.auth_have_account
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}
