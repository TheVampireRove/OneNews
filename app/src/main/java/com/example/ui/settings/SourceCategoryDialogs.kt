package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FeedCategoryDetector
import com.example.data.FeedCategoryOption
import com.example.data.FeedChannelPreview
import com.example.model.NewsCategory
import com.example.model.NewsSource
import com.example.ui.components.GlassContainer
import com.example.ui.components.glassBorderBrush
import com.example.ui.components.glassFillColor
import com.example.ui.components.rememberOneNewsHaptics
import com.example.ui.theme.OneUiCardRadius
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.net.URI

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFeedDialog(
    customCategories: List<String>,
    onPreviewFeed: suspend (String) -> FeedChannelPreview,
    onCreateCustomCategory: (String) -> Unit,
    onSaveFeed: (url: String, title: String, category: NewsCategory, customCategory: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberOneNewsHaptics()
    var manualUrl by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    // Never default to Tech — user must choose or confirm an auto-detected category
    var selectedCategoryOption by remember { mutableStateOf<FeedCategoryOption?>(null) }
    var userOverrodeCategory by remember { mutableStateOf(false) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectionReason by remember { mutableStateOf<String?>(null) }
    var detectedValidFeed by remember { mutableStateOf<Boolean?>(null) }
    var detectionTrigger by remember { mutableIntStateOf(0) }

    var showNewCategoryInput by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var localCustomCategories by remember(customCategories) { mutableStateOf(customCategories) }

    val categoryOptions = remember(localCustomCategories) {
        FeedCategoryDetector.buildCategoryOptions(localCustomCategories)
    }

    LaunchedEffect(manualUrl, detectionTrigger) {
        val trimmed = manualUrl.trim()
        if (trimmed.isEmpty()) {
            isDetecting = false
            detectionReason = null
            detectedValidFeed = null
            return@LaunchedEffect
        }

        // Immediate keyword/domain pre-selection while typing or pasting a URL
        val (quickCategory, quickReason) = FeedCategoryDetector.suggestCategoryFromMetadata(trimmed)
        if (!userOverrodeCategory && quickCategory != null) {
            selectedCategoryOption = FeedCategoryOption(quickCategory)
            detectionReason = quickReason
        }

        if (!trimmed.contains('.') && !trimmed.startsWith("http", ignoreCase = true)) {
            isDetecting = false
            return@LaunchedEffect
        }

        delay(380L)
        isDetecting = true
        try {
            val preview = onPreviewFeed(trimmed)
            if (manualName.isBlank() && !preview.channelTitle.isNullOrBlank()) {
                manualName = preview.channelTitle
            }
            if (preview.isValidFeed) {
                detectedValidFeed = true
            }
            if (!userOverrodeCategory && preview.suggestedCategory != null) {
                selectedCategoryOption = FeedCategoryOption(preview.suggestedCategory)
                detectionReason = preview.detectionReason
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            isDetecting = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassContainer(
            shape = RoundedCornerShape(OneUiCardRadius),
            fillAlpha = 0.94f,
            baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            highlighted = true,
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .testTag("add_feed_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.RssFeed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Add RSS / Atom Feed",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Assign this feed to a specific category tab",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("add_feed_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close add feed dialog",
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = manualUrl,
                        onValueChange = { manualUrl = it },
                        label = { Text("RSS / Atom Feed URL") },
                        placeholder = { Text("https://example.com/feed.xml") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            when {
                                isDetecting -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                                manualUrl.isNotBlank() -> {
                                    IconButton(
                                        onClick = {
                                            haptics.tap()
                                            userOverrodeCategory = false
                                            detectionTrigger++
                                        },
                                        modifier = Modifier.testTag("detect_feed_metadata_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = "Auto-detect feed title and category",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_source_url_input"),
                    )

                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("Feed Title (e.g. NASA Earth Observatory)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_source_name_input"),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Category (Required)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val statusLabel = selectedCategoryOption?.label ?: "Choose one"
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedCategoryOption != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        if (detectionReason != null && !userOverrodeCategory && selectedCategoryOption != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auto_detected_category_banner"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Suggested: ${selectedCategoryOption?.label} ($detectionReason). Tap any chip to change.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        } else if (detectedValidFeed == true) {
                            Text(
                                text = "Feed XML validated. Confirm category below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            categoryOptions.forEach { option ->
                                val picked = selectedCategoryOption?.key == option.key
                                CategoryPickerChip(
                                    option = option,
                                    selected = picked,
                                    onClick = {
                                        haptics.toggle()
                                        userOverrodeCategory = true
                                        selectedCategoryOption = option
                                    },
                                    testTag = "add_feed_category_${option.key.replace(':', '_').replace(' ', '_')}",
                                )
                            }

                            Surface(
                                onClick = {
                                    haptics.tap()
                                    showNewCategoryInput = !showNewCategoryInput
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                border = BorderStroke(1.dp, glassBorderBrush(highlighted = showNewCategoryInput)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("add_custom_category_chip"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text("New Category", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                }
                            }
                        }

                        AnimatedVisibility(visible = showNewCategoryInput) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = newCategoryInput,
                                    onValueChange = { newCategoryInput = it },
                                    label = { Text("Custom Category Name") },
                                    placeholder = { Text("e.g. Finance, Sports, Gaming") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("new_custom_category_input"),
                                )
                                Surface(
                                    onClick = {
                                        val clean = newCategoryInput.replace(Regex("[\\r\\n|]+"), " ").trim()
                                        if (clean.isNotBlank()) {
                                            haptics.confirm()
                                            if (localCustomCategories.none { it.equals(clean, ignoreCase = true) }) {
                                                localCustomCategories = (localCustomCategories + clean).sorted()
                                            }
                                            onCreateCustomCategory(clean)
                                            userOverrodeCategory = true
                                            selectedCategoryOption = FeedCategoryOption(NewsCategory.ALL, clean)
                                            newCategoryInput = ""
                                            showNewCategoryInput = false
                                        }
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("confirm_new_custom_category_button"),
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("Add", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }

                val selectedOption = selectedCategoryOption
                val canSave = selectedOption != null && (manualName.isNotBlank() || manualUrl.isNotBlank())

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            if (canSave && selectedOption != null) {
                                haptics.confirm()
                                val resolvedTitle = manualName.trim().ifBlank { deriveTitleFromUrl(manualUrl) }
                                onSaveFeed(
                                    manualUrl.trim(),
                                    resolvedTitle,
                                    selectedOption.builtIn,
                                    selectedOption.customName,
                                )
                                onDismiss()
                            }
                        },
                        enabled = canSave,
                        shape = CircleShape,
                        color = if (canSave) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (canSave) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_manual_source_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("Save & Enable", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReassignCategoryDialog(
    source: NewsSource,
    customCategories: List<String>,
    onCreateCustomCategory: (String) -> Unit,
    onReassign: (sourceId: String, category: NewsCategory, customCategory: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberOneNewsHaptics()
    var localCustomCategories by remember(customCategories) { mutableStateOf(customCategories) }
    val options = remember(localCustomCategories) {
        FeedCategoryDetector.buildCategoryOptions(localCustomCategories)
    }

    var selectedOption by remember(source) {
        mutableStateOf(
            FeedCategoryOption(
                builtIn = if (source.cleanCustomCategory != null) NewsCategory.ALL else source.category,
                customName = source.cleanCustomCategory,
            )
        )
    }
    var showNewCategoryInput by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassContainer(
            shape = RoundedCornerShape(OneUiCardRadius),
            fillAlpha = 0.94f,
            baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            highlighted = true,
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .testTag("reassign_category_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Label,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Reassign Feed Category",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close category reassign dialog")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Currently assigned to ${source.assignedCategoryLabel}. Pick a new tab below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.forEach { option ->
                            CategoryPickerChip(
                                option = option,
                                selected = selectedOption.key == option.key,
                                onClick = {
                                    haptics.toggle()
                                    selectedOption = option
                                },
                                testTag = "reassign_category_${option.key.replace(':', '_').replace(' ', '_')}",
                            )
                        }

                        Surface(
                            onClick = {
                                haptics.tap()
                                showNewCategoryInput = !showNewCategoryInput
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            border = BorderStroke(1.dp, glassBorderBrush(highlighted = showNewCategoryInput)),
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("New Category", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        }
                    }

                    AnimatedVisibility(visible = showNewCategoryInput) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = newCategoryInput,
                                onValueChange = { newCategoryInput = it },
                                label = { Text("Custom Category Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                onClick = {
                                    val clean = newCategoryInput.replace(Regex("[\\r\\n|]+"), " ").trim()
                                    if (clean.isNotBlank()) {
                                        haptics.confirm()
                                        if (localCustomCategories.none { it.equals(clean, ignoreCase = true) }) {
                                            localCustomCategories = (localCustomCategories + clean).sorted()
                                        }
                                        onCreateCustomCategory(clean)
                                        selectedOption = FeedCategoryOption(NewsCategory.ALL, clean)
                                        newCategoryInput = ""
                                        showNewCategoryInput = false
                                    }
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Add", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            haptics.confirm()
                            onReassign(source.id, selectedOption.builtIn, selectedOption.customName)
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("confirm_reassign_category_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("Update Category", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPickerChip(
    option: FeedCategoryOption,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            glassFillColor(
                alpha = 0.62f,
                baseColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(1.dp, glassBorderBrush(highlighted = selected)),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private fun deriveTitleFromUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    return try {
        val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val host = URI(withScheme).host.orEmpty().removePrefix("www.").removePrefix("feeds.")
        host.substringBefore('.').replaceFirstChar { it.uppercaseChar() }.ifBlank { "Custom Feed" }
    } catch (_: Exception) {
        "Custom Feed"
    }
}
