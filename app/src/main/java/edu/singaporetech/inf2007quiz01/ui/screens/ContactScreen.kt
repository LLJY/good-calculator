package edu.singaporetech.inf2007quiz01.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import edu.singaporetech.inf2007quiz01.R
import edu.singaporetech.inf2007quiz01.ui.theme.AvatarColors

/**
 * Contact screen — scrollable list of 30 CalBot cards.
 * Each card gets a unique accent color based on its ID for
 * that premium contact-list look. Items animate smoothly
 * when a CalBot gets promoted to the top after computation.
 *
 * @param calBotOrder the current display order (changes when a CalBot gets promoted)
 * @param onCalBotSelected called with the CalBot ID when a card is tapped
 */
@Composable
fun ContactScreen(
    calBotOrder: List<Int>,
    calBotMoods: Map<Int, String> = emptyMap(),
    onCalBotSelected: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calBotList")
    ) {
        items(calBotOrder, key = { it }, contentType = { "calBotCard" }) { calBotId ->
            val avatarColor = AvatarColors[calBotId % AvatarColors.size]

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable { onCalBotSelected(calBotId) }
                    .testTag("calBotItem")
                    .animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    /* Colored circular avatar — each CalBot gets a unique accent */
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(avatarColor)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = "CalBot Icon",
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("calBotIcon"),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.surface)
                        )
                    }

                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = "CalBot $calBotId",
                            modifier = Modifier.testTag("calBotName"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = calBotMoods[calBotId] ?: "Calculator #$calBotId",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
