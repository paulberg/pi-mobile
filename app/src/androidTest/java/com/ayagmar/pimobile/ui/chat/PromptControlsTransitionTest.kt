package com.ayagmar.pimobile.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayagmar.pimobile.chat.ChatViewModel
import com.ayagmar.pimobile.chat.PendingQueueItem
import com.ayagmar.pimobile.chat.PendingQueueType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromptControlsTransitionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun streamingControlsStayMountedAndToggleEnabledWithStreaming() {
        var isStreaming by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                PromptControls(
                    isStreaming = isStreaming,
                    isRetrying = false,
                    pendingQueueItems = emptyList(),
                    steeringMode = ChatViewModel.DELIVERY_MODE_ALL,
                    followUpMode = ChatViewModel.DELIVERY_MODE_ALL,
                    inputText = "hello",
                    pendingImages = emptyList(),
                    callbacks = noOpPromptControlsCallbacks(),
                )
            }
        }

        composeRule.onNodeWithTag(CHAT_PROMPT_INPUT_ROW_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CHAT_STREAMING_CONTROLS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Abort").assertIsNotEnabled()
        composeRule.onNodeWithText("Steer").assertIsNotEnabled()

        composeRule.runOnUiThread { isStreaming = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CHAT_PROMPT_INPUT_ROW_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CHAT_STREAMING_CONTROLS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Abort").assertIsEnabled()
        composeRule.onNodeWithText("Steer").assertIsEnabled()

        composeRule.runOnUiThread { isStreaming = false }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CHAT_PROMPT_INPUT_ROW_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CHAT_STREAMING_CONTROLS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Abort").assertIsNotEnabled()
    }

    @Test
    fun sendWhileStreamingQueuesAsFollowUpAndClearsInput() {
        var isStreaming by mutableStateOf(true)
        var inputText by mutableStateOf("do the thing")
        var lastSend = 0
        var lastFollowUp: String? = null

        composeRule.setContent {
            MaterialTheme {
                PromptControls(
                    isStreaming = isStreaming,
                    isRetrying = false,
                    pendingQueueItems = emptyList(),
                    steeringMode = ChatViewModel.DELIVERY_MODE_ALL,
                    followUpMode = ChatViewModel.DELIVERY_MODE_ALL,
                    inputText = inputText,
                    pendingImages = emptyList(),
                    callbacks =
                        noOpPromptControlsCallbacks().copy(
                            onInputTextChanged = { inputText = it },
                            onSendPrompt = { lastSend++ },
                            onFollowUp = { lastFollowUp = it },
                        ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitForIdle()

        assert(lastFollowUp == "do the thing") { "expected follow-up, got $lastFollowUp" }
        assert(lastSend == 0) { "send should not fire while streaming" }
        assert(inputText.isEmpty()) { "input should clear after follow-up" }
    }

    @Test
    fun pendingQueueInspectorAppearsOnlyWhileStreaming() {
        var isStreaming by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                PromptControls(
                    isStreaming = isStreaming,
                    isRetrying = false,
                    pendingQueueItems =
                        listOf(
                            PendingQueueItem(
                                id = "p-1",
                                type = PendingQueueType.STEER,
                                message = "focus on tests",
                                mode = ChatViewModel.DELIVERY_MODE_ALL,
                            ),
                        ),
                    steeringMode = ChatViewModel.DELIVERY_MODE_ALL,
                    followUpMode = ChatViewModel.DELIVERY_MODE_ALL,
                    inputText = "",
                    pendingImages = emptyList(),
                    callbacks = noOpPromptControlsCallbacks(),
                )
            }
        }

        composeRule.onAllNodesWithTag(CHAT_STREAMING_CONTROLS_TAG).assertCountEquals(1)
        composeRule.onAllNodesWithTag(CHAT_PROMPT_INPUT_ROW_TAG).assertCountEquals(1)

        composeRule.runOnUiThread { isStreaming = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Pending queue (1)").assertIsDisplayed()
    }

    private fun noOpPromptControlsCallbacks(): PromptControlsCallbacks {
        return PromptControlsCallbacks(
            onInputTextChanged = {},
            onSendPrompt = {},
            onShowCommandPalette = {},
            onAddImage = {},
            onRemoveImage = {},
            onAbort = {},
            onAbortRetry = {},
            onSteer = {},
            onFollowUp = {},
            onRemovePendingQueueItem = {},
            onClearPendingQueueItems = {},
        )
    }
}
