package com.acv.reduxessentials

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.lifecycleScope
import com.acv.reduxessentials.Action.*
import com.acv.reduxessentials.ui.theme.ReduxEssentialsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class MainActivity : ComponentActivity() {
    private val store = Store(
        initialState = AppState(
            atm = ATM(0),
            error = false,
            loading = false
        ),
        coroutineContext = lifecycleScope.coroutineContext
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReduxEssentialsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    App(store)
                }
            }
        }
    }
}

data class AppState(
    val atm: ATM,
    val error: Boolean,
    val loading: Boolean,
)

sealed interface Action {
    object Retry : Action
    data class Deposit(val amount: String) : Action
    data class Withdrawal(val amount: String) : Action
    object Validating : Action
    data class DepositSucceed(val amount: Int) : Action
    object DepositError : Action
    data class WithdrawalSucceed(val amount: Int) : Action
    object WithdrawalError : Action
}

class Store(
    initialState: AppState,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    var state = MutableStateFlow(initialState)

    fun dispatch(action: Action) {
        launch(Dispatchers.IO) {
            state.value.sideEffects(action)
            state.value = state.value.reduce(action)
        }
    }

    private suspend fun AppState.sideEffects(action: Action) {
        when (action) {
            is Deposit -> {
                dispatch(Validating)
                validateTransaction(action.amount)?.let { dispatch(DepositSucceed(it)) }
                    ?: dispatch(DepositError)
            }
            is Withdrawal -> {
                dispatch(Validating)
                validateTransaction(action.amount)?.let { dispatch(WithdrawalSucceed(it)) }
                    ?: dispatch(WithdrawalError)
            }
        }
    }

    private fun AppState.reduce(action: Action): AppState =
        when (action) {
            is Deposit -> this
            is Withdrawal -> this
            is Validating -> state.value.copy(loading = true)
            is DepositError -> state.value.copy(error = true, loading = false)
            is DepositSucceed -> state.value.copy(
                error = false,
                loading = false,
                atm = state.value.atm.copy(balance = state.value.atm.balance + action.amount)
            )
            is WithdrawalError -> state.value.copy(error = true, loading = false)
            is WithdrawalSucceed -> state.value.copy(
                error = false,
                loading = false,
                atm = state.value.atm.copy(balance = state.value.atm.balance - action.amount)
            )
            Retry -> state.value.copy(
                error = false,
                loading = false,
            )
        }
}

data class ATM(
    val balance: Int,
)

suspend fun validateTransaction(amount: String): Int? {
    delay(2000)
    return amount.validate()
}

@Composable
private fun App(store: Store) {
    val state by store.state.collectAsState()
    var amount by remember { mutableStateOf("") }

    val deposit = {
        store.dispatch(Deposit(amount))
        amount = ""
    }
    val withdraw = {
        store.dispatch(Withdrawal(amount))
        amount = ""
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            state.error -> {
                Column {
                    Text("Error")
                    Button(onClick = { store.dispatch(Retry) }) {
                        Text(text = "Retry")
                    }
                }
            }
            state.loading -> {
                Text(text = "Loading...")
            }
            else -> {
                Text(text = state.atm.balance.toString())

                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row {
                    Button(modifier = Modifier.padding(8.dp), onClick = deposit) {
                        Text(text = "Deposit")
                    }
                    Button(modifier = Modifier.padding(8.dp), onClick = withdraw) {
                        Text(text = "Withdraw")
                    }
                }
            }
        }
    }
}

private fun String.validate(): Int? =
    when {
        isDigitsOnly() -> toIntOrNull()
        else -> null
    }

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    ReduxEssentialsTheme {
        Greeting("Android")
    }
}