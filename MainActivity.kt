package edu.singaporetech.inf2007quiz01

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ServiceCompat.stopForeground
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import edu.singaporetech.inf2007quiz01.ui.theme.Inf2007quiz01Theme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// for displaying the caculator pad
data class CalculatorButton (
    val text:String = "",
    val isDigit:Boolean = true
)
// 5 rows
val CalculatorPadRow = arrayListOf(
    arrayListOf(CalculatorButton("AC", false), CalculatorButton("DEL", false), CalculatorButton("FIB", false), CalculatorButton("/", false)),
    arrayListOf(CalculatorButton("7"), CalculatorButton("8"), CalculatorButton("9"), CalculatorButton("*", false)),
    arrayListOf(CalculatorButton("4"), CalculatorButton("5"), CalculatorButton("6"), CalculatorButton("-", false)),
    arrayListOf(CalculatorButton("1"), CalculatorButton("2"), CalculatorButton("3"), CalculatorButton("+", false)),
    arrayListOf(CalculatorButton("0"), CalculatorButton("=", false)),
)

class MainActivity : ComponentActivity() {

    // TODO: use ViewModel to handle the states and logic
    // this is a dummy state
    var state by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Inf2007quiz01Theme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // TODO: utilize configuration.orientation and Configuration.ORIENTATION_LANDSCAPE
                    //  for handling configration change
                    val configuration = LocalConfiguration.current

                    Column {
                        TextField(
                            // TODO: display the result
                            value  = state,
                            onValueChange = {},
                            textStyle = TextStyle(
                                textAlign = TextAlign.End,
                                fontWeight = FontWeight.Black,
                                lineHeight = 640.sp,
                                fontSize = 60.sp,
                            ),
                            maxLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .testTag("display")
                        )

                        Divider(
                            color = Color.Gray,
                            modifier = Modifier
                                .fillMaxWidth()  //fill the max height
                                .width(1.dp)
                        )

                        Spacer(modifier = Modifier.weight(1.0f))

                        Column {
                            LazyColumn (
                                modifier = Modifier
                                    .background(Color.LightGray)
                                    .weight(1f)
                                    .testTag("history"),
                            ) {
                                //TODO: display the expression history
                            }
                            Divider(
                                color = Color.Gray,
                                modifier = Modifier
                                    .fillMaxWidth()  //fill the max height
                                    .width(1.dp)
                            )
                            CalculatorPad(modifier = Modifier
                                .weight(1f)
                                .wrapContentSize())
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CalculatorPad(modifier: Modifier = Modifier) {
        val buttonSpacing = 2.dp
        Column(
            modifier = modifier.then(Modifier.padding(2.dp))
                .testTag("calculatorPad"),
            verticalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            // the first 4 rows
            for (row in 0..3){
                Row(
                    modifier = Modifier
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                ) {
                    for (btn in CalculatorPadRow[row])
                        CalculatorButtonNode(btn, Modifier.weight(1f))
                }
            }

            // add a switch to the 5th row
            Row(
                modifier = Modifier
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
            ) {
                Box(contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            color = Color.Gray,
                            shape = CircleShape
                        )) {
                    Row (verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(buttonSpacing)){
                        Text(text = "API",
                            fontSize = 20.sp,
                            color = Color.White,
                        )
                        Switch(
                            // TODO: implement the toggle logic
                            checked = false,
                            modifier = Modifier.testTag("toggleAPI"),
                            onCheckedChange = {
                            }
                        )
                    }
                }

                for (btn in CalculatorPadRow[4])
                    CalculatorButtonNode(btn, Modifier.weight(1f))
            }
        }
    }

    @Composable
    fun CalculatorButtonNode(
        button: CalculatorButton,
        modifier: Modifier = Modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(
                    color = if (button.isDigit) Color.LightGray else Color.DarkGray,
                    shape = CircleShape
                )
                .fillMaxHeight()
                .clickable {
                    // TODO: implement the logic and handle the button click in viewmodel
                    // this is a dummy input
                    state+=button.text
                }
                .testTag("button${button.text}")
                .then(modifier)
        ) {
            Text(
                text = button.text,
                fontSize = 30.sp,
                color = Color.White
            )
        }
    }
}