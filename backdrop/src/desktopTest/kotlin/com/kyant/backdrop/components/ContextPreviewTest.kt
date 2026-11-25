package com.kyant.backdrop.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class ContextPreviewTest {
    private fun row(prepare: () -> Unit = {}) =
        MenuTrigger(LiquidMenuActivation.ListContext, prepare, {}, {})

    @Test fun thePressedRowSurvivesActivationAndFingerUpUntilItsReturnCompletes() {
        val state = LiquidMenuState {}
        var opened = 0
        val source = row { opened++ }
        state.triggers.add(source)
        val at = Offset(42f, 17f)
        state.beginHold(at, source)
        assertSame(source, state.previewTrigger)
        assertFalse(state.expanded)
        state.openFrom(source)
        state.finishHold()
        state.retirePreview(source)
        assertSame(source, state.previewTrigger)
        assertEquals(at, state.openingPoint)
        assertEquals(1, opened)
        state.dismiss()
        state.retirePreview(source)
        assertNull(state.previewTrigger)
    }

    @Test fun aCancelledRowCannotRetireTheNextHeldRow() {
        val state = LiquidMenuState {}
        val first = row()
        val next = row()
        state.beginHold(Offset.Zero, first)
        state.finishHold()
        state.beginHold(Offset.Zero, next)
        state.retirePreview(first)
        assertSame(next, state.previewTrigger)
        state.finishHold()
        state.retirePreview(next)
        assertNull(state.previewTrigger)
        assertFalse(state.expanded)
    }

    @Test fun removingThePreviewedItemBeforeAnActionDismissesItsMenu() {
        val state = LiquidMenuState { fail("Removing the source must not select a menu item") }
        val source = row()
        state.triggers.add(source)
        state.beginHold(Offset.Zero, source)
        state.openFrom(source)
        state.removeTrigger(source)
        assertFalse(state.expanded)
        assertNull(state.heldTrigger)
        assertNull(state.previewTrigger)
    }
}
