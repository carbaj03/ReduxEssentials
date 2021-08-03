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
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.acv.reduxessentials.Action.*
import com.acv.reduxessentials.ui.theme.ReduxEssentialsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*


fun createStore(
    initialState: AppState,
    reducers: List<Reducer>,
    sideEffects: List<SideEffect>,
    coroutineScope: CoroutineScope
): Store {
    val currentState = MutableStateFlow(initialState)

    val initialDispatcher: (Action) -> Unit = { action ->
        Log.e("initial", action.toString())
        currentState.value = reducers.fold(currentState.value) { state, reducer ->
            reducer(action, state)
        }
    }

    val dispatcher: (Action) -> Unit =
        sideEffects.foldRight(initialDispatcher) { sideEffect, dispatcher ->
            { action ->
                Log.e("sideEffect", action.toString())
                coroutineScope.launch { sideEffect(action, { dispatcher(it) }, currentState.value) }
            }
        }

    return object : Store {
        override var dispatch: (Action) -> Unit = dispatcher
        override val state: StateFlow<AppState> = currentState
    }
}


class ComposeNavigator(
    private var navController: NavController? = null,
) : Navigator {
    override fun goTo(screen: Screen) {
        when (screen) {
            Screen.Atm -> navController?.navigate("atm")
            Screen.Transaction -> navController?.navigate("transaction")
        }
    }

    @Composable
    fun create(navHostController: NavHostController, store: Store) {
        navController = navHostController

        NavHost(navController = navHostController, startDestination = "atm") {
            composable("atm") { Atm(store) }
            composable("transaction") { Atm(store) }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val navigator: ComposeNavigator = ComposeNavigator()

    private val store = createStore(
        initialState = AppState(
            atm = ATM(0),
            error = false,
            loading = false,
            transactions = emptyList()
        ),
        sideEffects = listOf(ASYNC, TRACKER, Navigation(navigator)),
        reducers = listOf(AtmReducer, TransactionReducer),
        coroutineScope = lifecycleScope
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReduxEssentialsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    App(navigator, store)
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
    data class EditTransaction(val id: Long) : Action
}

typealias SideEffect = suspend (Action, (Action) -> Unit, AppState) -> Unit

interface Store {
    val state: StateFlow<AppState>
    var dispatch: (Action) -> Unit
}

val ASYNC: SideEffect = { action, dispatch, state ->
    Log.e("async", action.toString())
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
    dispatch(action)
}

interface Navigator {
    fun goTo(screen: Screen)
}

sealed interface Screen {
    object Atm : Screen
    object Transaction : Screen
}

fun Navigation(navigator: Navigator): SideEffect = { action, dispatch, state ->
    Log.e("nav", action.toString())
    when (action) {
        is EditTransaction -> navigator.goTo(Screen.Transaction)
    }
    dispatch(action)
}

typealias Reducer = (Action, AppState) -> AppState

val TransactionReducer: Reducer = { action, state ->
    when (action) {
        is RemoveTransaction -> state.copy(
            atm = state.atm.copy(
                balance = state.transactions.firstOrNull { it.id == action.id }?.let {
                    when (it.type) {
                        Type.Expense -> state.atm.balance + it.amount
                        Type.Income -> state.atm.balance - it.amount
                    }
                } ?: state.atm.balance),
            transactions = state.transactions.filterNot { it.id == action.id },
        )
        else -> state
    }
}

val AtmReducer: Reducer = { action, state ->
    when (action) {
        is Deposit -> state
        is Withdrawal -> state
        is Validating -> state.copy(loading = true)
        is DepositError -> state.copy(error = true, loading = false)
        is DepositSucceed -> state.copy(
            error = false,
            loading = false,
            atm = state.atm.copy(balance = state.atm.balance + action.amount),
            transactions = state.transactions.plus(
                Transaction(
                    UUID.randomUUID().leastSignificantBits,
                    action.amount,
                    Type.Income,
                    ""
                )
            )
        )
        is WithdrawalError -> state.copy(error = true, loading = false)
        is WithdrawalSucceed -> state.copy(
            error = false,
            loading = false,
            atm = state.atm.copy(balance = state.atm.balance - action.amount),
            transactions = state.transactions.plus(
                Transaction(
                    UUID.randomUUID().leastSignificantBits,
                    action.amount,
                    Type.Expense,
                    ""
                )
            ),
        )
        is Retry -> state.copy(
            error = false,
            loading = false,
        )
        else -> state
    }
}

val TRACKER: SideEffect = { action, dispatch, state ->
    Log.e("tracker", action.toString())
    when (action) {
        is Deposit -> Log.e("tracker-deposit", state.toString())
        is Withdrawal -> Log.e("tracker-withdraw", state.toString())
    }
    dispatch(action)
}

data class ATM(
    val balance: Int,
)

suspend fun validateTransaction(amount: String): Int? {
    delay(1000)
    return amount.validate()
}

@Composable
private fun App(navigator: ComposeNavigator, store: Store) {
    navigator.create(navHostController = rememberNavController(), store = store)
}

@Composable
private fun Atm(store: Store) {
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

    val edit = { id: Long ->
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
                        Button(onClick = { edit(it.id) }) {
                            Text(text = "Edit")
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