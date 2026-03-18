package edu.singaporetech.inf2007quiz01.ui.screens

import android.content.res.Configuration
import android.media.MediaPlayer
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.singaporetech.inf2007quiz01.ui.theme.CalcDigitGray
import edu.singaporetech.inf2007quiz01.ui.theme.CalcOrange
import edu.singaporetech.inf2007quiz01.ui.theme.CalcRed
import edu.singaporetech.inf2007quiz01.FortranBridge
import edu.singaporetech.inf2007quiz01.R
import edu.singaporetech.inf2007quiz01.gl.SphereGLView
import edu.singaporetech.inf2007quiz01.gl.SphereRenderer
import edu.singaporetech.inf2007quiz01.ui.theme.CalcTeal

/**
 * Represents a single button on the calculator pad.
 * Marked @Immutable so Compose knows the button grid never changes
 * and can skip recomposing static rows entirely.
 */
@Immutable
data class CalculatorButton(
    val text: String = "",
    val isDigit: Boolean = true
)

/**
 * Button layout — 5 rows.
 * Last row (0 and =) is handled separately because the API toggle sits next to them.
 */
val CalculatorPadRow = arrayListOf(
    arrayListOf(CalculatorButton("AC", false), CalculatorButton("DEL", false), CalculatorButton("FIB", false), CalculatorButton("/", false)),
    arrayListOf(CalculatorButton("7"), CalculatorButton("8"), CalculatorButton("9"), CalculatorButton("*", false)),
    arrayListOf(CalculatorButton("4"), CalculatorButton("5"), CalculatorButton("6"), CalculatorButton("-", false)),
    arrayListOf(CalculatorButton("1"), CalculatorButton("2"), CalculatorButton("3"), CalculatorButton("+", false)),
    arrayListOf(CalculatorButton("0"), CalculatorButton("=", false)),
)

/**
 * Calculator screen — display + history + button pad.
 * In portrait the history sits above the pad; in landscape it moves to the left.
 * The whole thing uses the theme's surface colors for a polished dark-mode calculator look.
 */
@Composable
fun CalculatorScreen(
    calBotName: String,
    displayText: String,
    history: List<String>,
    isApiEnabled: Boolean,
    mood: String = "awaiting consciousness",
    onButtonClick: (String) -> Unit,
    onToggleApi: (Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // The sacred "faaah" sound — plays every time "=" is pressed.
    val context = LocalContext.current
    val faaahPlayer = remember {
        MediaPlayer.create(context, R.raw.faaah)?.apply {
            setOnCompletionListener { seekTo(0) }
        }
    }
    DisposableEffect(Unit) {
        onDispose { faaahPlayer?.release() }
    }

    // OpenGL renderer for the Fortran raytraced sphere
    val sphereRenderer = remember { SphereRenderer() }

    val wrappedOnButtonClick: (String) -> Unit = remember(onButtonClick, faaahPlayer, sphereRenderer) {
        { text: String ->
            if (text == "=") {
                faaahPlayer?.let {
                    if (it.isPlaying) it.seekTo(0)
                    it.start()
                }
                // Trigger Fortran raytrace and feed pixels to GL renderer
                try {
                    val pixels = FortranBridge.getPixels()
                    sphereRenderer.updatePixels(pixels)
                } catch (_: Throwable) { /* Fortran .so not available */ }
            }
            onButtonClick(text)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Column {
            /* CalBot name header with mood and Fortran-raytraced GL sphere */
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = calBotName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = mood,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                // 48x48dp OpenGL ES 2.0 surface rendering a Fortran-raytraced
                // sphere with GLSL CRT scanline + chromatic aberration shaders
                SphereGLView(
                    renderer = sphereRenderer,
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, CalcTeal, RoundedCornerShape(8.dp))
                )
            }

            /* Expression / result display — styled as a clean readout with no borders */
            TextField(
                value = displayText,
                onValueChange = {},
                textStyle = TextStyle(
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Light,
                    lineHeight = 640.sp,
                    fontSize = 56.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                maxLines = 2,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("display")
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .width(1.dp)
            )

            if (isLandscape) {
                /* Landscape: history on the left, pad on the right */
                Row(modifier = Modifier.weight(1f)) {
                    HistoryPanel(
                        history = history,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    CalculatorPad(
                        onButtonClick = wrappedOnButtonClick,
                        isApiEnabled = isApiEnabled,
                        onToggleApi = onToggleApi,
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentSize()
                    )
                }
            } else {
                /* Portrait: spacer pushes history + pad to the bottom half */
                Spacer(modifier = Modifier.weight(1.0f))

                Column {
                    HistoryPanel(
                        history = history,
                        modifier = Modifier.weight(1f)
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .width(1.dp)
                    )

                    CalculatorPad(
                        onButtonClick = wrappedOnButtonClick,
                        isApiEnabled = isApiEnabled,
                        onToggleApi = onToggleApi,
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentSize()
                    )
                }
            }
        }
    }
}

/**
 * History panel — shows recent expressions in a scrollable list.
 * Extracted so portrait and landscape can reuse the same component.
 * Items animate in/out smoothly via animateItem.
 */
@Composable
private fun HistoryPanel(
    history: List<String>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .testTag("history"),
    ) {
        items(history, contentType = { "historyEntry" }) { expression ->
            Text(
                text = expression,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .animateItem(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The button grid plus the API toggle in the bottom-left corner. */
@Composable
fun CalculatorPad(
    onButtonClick: (String) -> Unit,
    isApiEnabled: Boolean,
    onToggleApi: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonSpacing = 3.dp
    Column(
        modifier = modifier.then(Modifier.padding(2.dp))
            .testTag("calculatorPad"),
        verticalArrangement = Arrangement.spacedBy(buttonSpacing)
    ) {
        /* Rows 0-3: standard button rows */
        for (row in 0..3) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
            ) {
                for (btn in CalculatorPadRow[row]) {
                    CalculatorButtonNode(
                        button = btn,
                        onClick = { onButtonClick(btn.text) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        /* Row 4: API toggle + "0" + "=" */
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                ) {
                    Text(
                        text = "API",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = isApiEnabled,
                        modifier = Modifier.testTag("toggleAPI"),
                        onCheckedChange = { onToggleApi(it) }
                    )
                }
            }

            for (btn in CalculatorPadRow[4]) {
                CalculatorButtonNode(
                    button = btn,
                    onClick = { onButtonClick(btn.text) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Figures out the background and text color for a calculator button
 * based on its function. Operators get orange, destructive actions
 * get red, FIB gets teal, digits stay neutral.
 */
private fun getButtonColors(text: String): Pair<Color, Color> = when (text) {
    "+", "-", "*", "/", "=" -> CalcOrange to Color.White
    "AC", "DEL" -> CalcRed to Color.White
    "FIB" -> CalcTeal to Color(0xFF1C1C1E)
    else -> CalcDigitGray to Color.White
}

/**
 * Individual calculator button with a spring scale-down animation
 * on press. No ripple — the physical "push" feel is the feedback.
 */
@Composable
fun CalculatorButtonNode(
    button: CalculatorButton,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "buttonScale"
    )

    val (bgColor, textColor) = getButtonColors(button.text)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(color = bgColor, shape = CircleShape)
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .testTag("button${button.text}")
    ) {
        Text(
            text = button.text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}
