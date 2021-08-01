package com.acv.reduxessentials

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Green
import androidx.compose.ui.graphics.Color.Companion.Red
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
import java.util.*
import kotlin.coroutines.CoroutineContext

class MainActivity : ComponentActivity() {
    private val store = Store(
        initialState = AppState(
            atm = ATM(0),
            error = false,
            loading = false,
            transactions = emptyList()
        ),
        coroutineContext = lifecycleScope.coroutineContext,
        sideEffects = listOf(ASYNC, TRACKER)
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
    val transactions: List<Transaction>,
    val error: Boolean,
    val loading: Boolean,
)

data class Transaction(
    val id: Long,
    val amount: Int,
    val type: Type,
    val description: String
)

sealed interface Type {
    object Expense : Type
    object Income : Type
}

sealed interface Action {
    object Retry : Action
    data class Deposit(val amount: String) : Action
    data class Withdrawal(val amount: String) : Action
    object Validating : Action
    data class DepositSucceed(val amount: Int) : Action
    object DepositError : Action
    data class WithdrawalSucceed(val amount: Int) : Action
    object WithdrawalError : Action
    data class RemoveTransaction(val id: Long) : Action
}

typealias SideEffect = suspend (Action, Store) -> Unit

class Store(
    initialState: AppState,
    private val sideEffects: List<SideEffect>,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    var state = MutableStateFlow(initialState)

    fun dispatch(action: Action) {
        launch(Dispatchers.IO) {
            state.value = state.value.reduce(action)
            sideEffects.run { forEach { it(action, this@Store) } }
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
                atm = state.value.atm.copy(balance = state.value.atm.balance + action.amount),
                transactions = state.value.transactions.plus(Transaction(UUID.randomUUID().leastSignificantBits, action.amount, Type.Income, ""))
            )
            is WithdrawalError -> state.value.copy(error = true, loading = false)
            is WithdrawalSucceed -> state.value.copy(
                error = false,
                loading = false,
                atm = state.value.atm.copy(balance = state.value.atm.balance - action.amount),
                transactions = state.value.transactions.plus(Transaction(UUID.randomUUID().leastSignificantBits, action.amount, Type.Expense, "")),
            )
            is Retry -> state.value.copy(
                error = false,
                loading = false,
            )
            is RemoveTransaction -> state.value.copy(
                atm =  state.value.atm.copy(balance = state.value.transactions.firstOrNull { it.id == action.id }?.let {
                  when(it.type){
                      Type.Expense ->  state.value.atm.balance + it.amount
                      Type.Income ->  state.value.atm.balance - it.amount
                  }
                } ?: state.value.atm.balance),
                transactions = state.value.transactions.filterNot { it.id == action.id },
            )
        }
}

val ASYNC: SideEffect = { action, store ->
    when (action) {
        is Deposit -> {
            store.dispatch(Validating)
            validateTransaction(action.amount)?.let { store.dispatch(DepositSucceed(it)) }
                ?: store.dispatch(DepositError)
        }
        is Withdrawal -> {
            store.dispatch(Validating)
            validateTransaction(action.amount)?.let { store.dispatch(WithdrawalSucceed(it)) }
                ?: store.dispatch(WithdrawalError)
        }
    }
}

val TRACKER: SideEffect = { action, store ->
    when (action) {
        is Deposit -> Log.e("deposit", store.state.value.toString())
        is Withdrawal -> Log.e("deposit", store.state.value.toString())
    }
}

data class ATM(
    val balance: Int,
)

suspend fun validateTransaction(amount: String): Int? {
    delay(1000)
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

    val remove = { id: Long ->
        store.dispatch(RemoveTransaction(id))
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

                Spacer(modifier = Modifier.height(20.dp))

                LazyColumn {
                    items(state.transactions) {
                        Text(
                            text = it.amount.toString(),
                            color = when (it.type) {
                                is Type.Income -> Green
                                is Type.Expense -> Red
                            }
                        )
                        Button(onClick = { remove(it.id) }) {
                            Text(text = "Remove")
                        }
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