# inf2007-quiz01-2026

## Feature Checklist

1. **Refine the UI** - Done. Contact Screen with LazyColumn of 30 CalBot Cards (icon + name). Calculator Screen with display, history, and calculator pad. Landscape mode: history arranged to the left of the calculator pad. All widgets visible after rotation.
2. **Basic arithmetic computation** - Done. Handles number1 operator number2 for +, -, *, /. Result replaces expression and is reusable for chained operations.
3. **DEL and AC functions** - Done. AC clears display. DEL backspaces one character (works on results too).
4. **Display of expression history** - Done. LazyColumn shows expression history (most recent first, max 20 per CalBot). Only expressions stored, not results.
5. **Toggle API and computation using mathjs with Retrofit** - Done. Switch toggles API mode. When enabled, expressions are evaluated via math.js API (https://api.mathjs.org/v4/) through Retrofit on IO dispatcher. Toggle state persisted in DataStore per CalBot.
6. **Fibonacci computation without blocking main UI** - Done. FIB button immediately adds "fib(n)" to history, dispatches computation to Dispatchers.Default. UI remains responsive during fib(44). Uses FunctionMap.kt.
7. **Persistent history and toggle API** - Done. History stored in Room database per CalBot (survives app relaunch). API toggle stored in Preferences DataStore per CalBot (survives app relaunch).
8. **Navigation with per-CalBot history** - Done. Navigation 3 (NavDisplay) with CalBot ID passed via route. Hilt ViewModel with AssistedInject scoped per NavEntry. Only selected CalBot's history is shown.
9. **Back from calculator** - Done. If "=" was pressed, the CalBot is moved to the top of the list on back press. If no computation was done, list order unchanged. Display cleared when selecting a new CalBot. Survives rotation (ViewModel-based state).
10. **Code quality** - Done. Modular architecture with clean separation: data layer (Room, DataStore, Retrofit), DI (Hilt Module), ViewModels (CalBotListViewModel, CalculatorViewModel), UI screens (ContactScreen, CalculatorScreen), navigation routes. Commented and properly indented.

## Architecture

- **Navigation**: Navigation 3 (NavDisplay, entryProvider DSL)
- **DI**: Hilt with @AssistedInject for per-CalBot ViewModel creation
- **Persistence**: Room Database (history) + Preferences DataStore (API toggle)
- **Network**: Retrofit 3.0.0 with GsonConverterFactory for math.js API
- **Threading**: Coroutines with Dispatchers.IO (API calls) and Dispatchers.Default (Fibonacci)

## Test your solution

* You may put and run the ./test.bat or ./test.sh in the test folder in the root directory of the project to run the Grading apk

  * The test result will be shown in the console
  * You need to set the Enviroment Variable for the test to run

    * Add adb to the PATH, i.e, add SDK\_location/platform-tools/, SDK\_location can be found in Settings->Language \& Frameworks->Android SDK
    * Add JAVA\_HOME to the PATH, just use the embedded Gradle JDK path in Android Studio, it can be found in Settings->Build, Execution, Deployment->Build Tools->Gradle
    * Ensure the Emulator or the device is connected and running
    * You may need to run multiple times
