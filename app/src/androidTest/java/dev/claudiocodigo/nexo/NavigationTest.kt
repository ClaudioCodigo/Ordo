package dev.claudiocodigo.nexo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testFiveTabsUseDistinctDestinations() {
        composeTestRule.onNodeWithTag("screen_hoje").assertExists()

        composeTestRule.onNodeWithTag("tab_agenda").performClick()
        composeTestRule.onNodeWithTag("screen_agenda").assertExists()

        composeTestRule.onNodeWithTag("tab_ferramentas").performClick()
        composeTestRule.onNodeWithTag("screen_ferramentas").assertExists()

        composeTestRule.onNodeWithTag("tab_cadastros").performClick()
        composeTestRule.onNodeWithTag("screen_cadastros").assertExists()

        composeTestRule.onNodeWithTag("tab_mais").performClick()
        composeTestRule.onNodeWithTag("screen_mais").assertExists()

        composeTestRule.onNodeWithTag("tab_hoje").performClick()
        composeTestRule.onNodeWithTag("screen_hoje").assertExists()
    }

    @Test
    fun testFabOpensNewOrderAndTechnicalRoutes() {
        composeTestRule.onNodeWithTag("tab_hoje").performClick()
        composeTestRule.onNodeWithContentDescription("Nova OS").performClick()
        composeTestRule.onNodeWithTag("screen_nova_os").assertExists()
        composeTestRule.onNodeWithContentDescription("Voltar").performClick()
        composeTestRule.onNodeWithTag("screen_hoje").assertExists()

        composeTestRule.onNodeWithTag("tab_ferramentas").performClick()
        composeTestRule.onNodeWithTag("screen_ferramentas").assertExists()
        composeTestRule.onNodeWithText("Diagnóstico de Bateria").performClick()
        composeTestRule.onNodeWithTag("screen_diagnostico").assertExists()
        composeTestRule.onNodeWithContentDescription("Voltar").performClick()

        composeTestRule.onNodeWithTag("tab_cadastros").performClick()
        composeTestRule.onNodeWithTag("screen_cadastros").assertExists()
        composeTestRule.onNodeWithText("Baterias").performClick()
        composeTestRule.onNodeWithTag("screen_lista_cadastro").assertExists()
    }

    @Test
    fun newOrderStartsFreshAndSavedDraftCanBeDeletedFromAgenda() {
        val uniqueTitle = "Rascunho ADB ${System.currentTimeMillis()}"

        composeTestRule.onNodeWithContentDescription("Nova OS").performClick()
        composeTestRule.onNodeWithTag("nova_os_title").performTextInput(uniqueTitle)
        composeTestRule.onNodeWithContentDescription("Voltar").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(uniqueTitle).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription("Nova OS").performClick()
        assertTrue(composeTestRule.onAllNodesWithText(uniqueTitle).fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithContentDescription("Voltar").performClick()

        composeTestRule.onNodeWithTag("tab_agenda").performClick()
        composeTestRule.onNodeWithTag("agenda_search").performTextInput(uniqueTitle)
        composeTestRule.onNodeWithContentDescription("Excluir rascunho local: $uniqueTitle").performClick()
        composeTestRule.onNodeWithTag("confirm_delete_draft").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithContentDescription("Excluir rascunho local: $uniqueTitle")
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }
}
