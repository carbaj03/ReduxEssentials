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
import com.acv.reduxessentials.ui.theme.ReduxEssentialsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

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
    data class Deposit(val amount: String) : Action
    data class Withdrawal(val amount: String) : Action
    object Validating : Action
    object DepositSucceed : Action
    object DepositError : Action
    object WithdrawalSucceed : Action
    object WithdrawalError : Action
}

class Store(
    initialState: AppState,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    var state = MutableStateFlow(initialState)

    fun dispatch(action: Action) {
        launch { state.value = state.value.reduce(action) }
    }

    private suspend fun AppState.reduce(action: Action): AppState =
        when (action) {
            is Action.Deposit -> {
                if (validateTransaction()) state.value.copy(state.value.atm.copy(state.value.atm.balance + action.amount.validate()))
                else this
            }
            is Action.Withdrawal -> {
                if (validateTransaction()) state.value.copy(state.value.atm.copy(state.value.atm.balance - action.amount.validate()))
                else this
            }
            is Action.Validating -> state.value.copy(loading = true)
            is Action.DepositError -> state.value.copy(error = true, loading = false)
            is Action.DepositSucceed -> state.value.copy(error = false, loading = false)
            is Action.WithdrawalError -> state.value.copy(error = true, loading = false)
            is Action.WithdrawalSucceed -> state.value.copy(error = false, loading = false)
        }
}

data class ATM(
    val balance: Int,
)

suspend fun validateTransaction(): Boolean {
    delay(8000)
    return Random.nextBoolean()
}

@Composable
private fun App(store: Store) {
    val state by store.state.collectAsState()
    var amount by remember { mutableStateOf("") }

    val deposit = {
        store.dispatch(Action.Deposit(amount))
        amount = ""
    }
    val withdraw = {
        store.dispatch(Action.Withdrawal(amount))
        amount = ""
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            state.error -> {
                Text("Error")
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

private fun String.validate(): Int =
    when {
        isDigitsOnly() -> toIntOrNull() ?: 0
        isBlank() -> 0
        else -> 0
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