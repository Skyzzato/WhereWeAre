package com.whereweare.app.ui

import com.whereweare.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun AuthScreen(vm: AuthViewModel) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords intentionally aren't put into saved instance state.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val operation by vm.operation.collectAsStateWithLifecycle()
    val confirmation by vm.confirmation.collectAsStateWithLifecycle()
    val deleted by vm.accountDeleted.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(40.dp))
        Text(stringResource(R.string.app_name),style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary)
        if(deleted) Text(Strings.text(R.string.ui_002))
        Text(stringResource(R.string.welcome),style=MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.welcome_detail),style=MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        if(registering) OutlinedTextField(name,{ name=it },label={ Text(stringResource(R.string.name)) },singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(email,{ email=it },label={ Text(stringResource(R.string.email)) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{ password=it },label={ Text(stringResource(R.string.password)) },visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,modifier=Modifier.fillMaxWidth())
        if(registering) OutlinedTextField(confirm,{ confirm=it },label={ Text(stringResource(R.string.confirm_password)) },visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,modifier=Modifier.fillMaxWidth())
        Busy(operation,inline=true)
        if(confirmation) Notice(R.string.confirmation)
        Button(onClick={ if(registering) vm.register(name,email,password,confirm) else vm.login(email,password) },enabled=!operation.busy,modifier=Modifier.fillMaxWidth()) {
            Text(stringResource(if(registering) R.string.register else R.string.login))
        }
        if(!registering) TextButton(onClick=vm::recoverPassword) { Text(Strings.text(R.string.ui_001)) }
        TextButton(onClick={ registering=!registering; vm.message(null) }) { Text(stringResource(if(registering) R.string.have_account else R.string.new_account)) }
    }
}
