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
import com.acv.reduxessentials.ui.theme.ReduxEssentialsTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReduxEssentialsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    App()
                }
            }
        }
    }
}

object Store {
    var atm = MutableStateFlow(ATM(0))

    fun deposit(amount: String) {
        atm.value = atm.value.copy(balance = atm.value.balance + amount.validate())
    }

    fun withdraw(amount: String) {
        atm.value = atm.value.copy(balance = atm.value.balance - amount.validate())
    }
}

data class ATM(
    val balance: Int,
)

@Composable
private fun App() {
    val atm by Store.atm.collectAsState()
    var amount by remember { mutableStateOf("") }

    val deposit = {
        Store.deposit(amount)
        amount = ""
    }
    val withdraw = {
        Store.withdraw(amount)
        amount = ""
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = atm.balance.toString())

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