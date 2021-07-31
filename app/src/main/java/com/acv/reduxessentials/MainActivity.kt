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

data class ATM(
    val balance: Int,
)

@Composable
private fun App() {
    var atm by remember { mutableStateOf(ATM(0)) }
    var amount by remember { mutableStateOf("") }

    val deposit = {
        atm = atm.copy(balance = atm.balance + amount.validate())
        amount = ""
    }
    val withdraw = {
        atm = atm.copy(balance = atm.balance - amount.validate())
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