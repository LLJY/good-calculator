package edu.singaporetech.inf2007quiz01

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import edu.singaporetech.inf2007quiz01.navigation.CalculatorRoute
import edu.singaporetech.inf2007quiz01.navigation.ContactRoute
import edu.singaporetech.inf2007quiz01.ui.screens.CalculatorScreen
import edu.singaporetech.inf2007quiz01.ui.screens.ContactScreen
import edu.singaporetech.inf2007quiz01.ui.theme.Inf2007quiz01Theme
import edu.singaporetech.inf2007quiz01.ui.viewmodel.CalBotListViewModel
import edu.singaporetech.inf2007quiz01.ui.viewmodel.CalculatorViewModel

/** Single activity — everything else is handled by Nav3 composables. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Inf2007quiz01Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalBotApp()
                }
            }
        }
    }
}

/**
 * Root composable — sets up Nav3 and wires the two screens together.
 *
 * CalBotListViewModel lives at the Activity level so the backstack and
 * CalBot ordering survive rotation without needing rememberSaveable.
 *
 * CalculatorViewModel is scoped per NavEntry via Hilt AssistedInject,
 * so each CalBot gets its own isolated calculator state.
 */
@Composable
fun CalBotApp() {
    val navViewModel: CalBotListViewModel = viewModel()

    NavDisplay(
        backStack = navViewModel.backStack,
        onBack = { navViewModel.navigateBack() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<ContactRoute> {
                ContactScreen(
                    calBotOrder = navViewModel.calBotOrder,
                    calBotMoods = navViewModel.calBotMoods,
                    onCalBotSelected = { calBotId ->
                        navViewModel.navigateTo(CalculatorRoute(calBotId))
                    }
                )
            }

            entry<CalculatorRoute> { key ->
                val calculatorVM = hiltViewModel<CalculatorViewModel, CalculatorViewModel.Factory>(
                    creationCallback = { factory -> factory.create(key.calBotId) }
                )

                // Wire mood updates from the LLM to the contact list
                calculatorVM.onMoodGenerated = { id, mood ->
                    navViewModel.updateMood(id, mood)
                }

                // tell the nav ViewModel when "=" has been pressed
                LaunchedEffect(calculatorVM.hasComputed) {
                    if (calculatorVM.hasComputed) {
                        navViewModel.markComputed(key.calBotId)
                    }
                }

                CalculatorScreen(
                    calBotName = "CalBot ${key.calBotId}",
                    displayText = calculatorVM.displayText,
                    history = calculatorVM.history,
                    isApiEnabled = calculatorVM.isApiEnabled,
                    mood = calculatorVM.currentMood,
                    onButtonClick = { calculatorVM.onButtonClick(it) },
                    onToggleApi = { calculatorVM.toggleApi(it) }
                )
            }
        }
    )
}
