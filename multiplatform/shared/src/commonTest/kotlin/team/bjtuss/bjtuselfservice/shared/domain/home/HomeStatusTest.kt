package team.bjtuss.bjtuselfservice.shared.domain.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeStatusTest {
    @Test fun derivesWarningsFromNumericValues() {
        val status = HomeStatus("3", "19.99", "0")
        assertTrue(status.hasNewMail)
        assertTrue(status.campusCardLow)
        assertTrue(status.networkEmpty)
    }

    @Test fun boundaryAndUnknownValuesDoNotCreateFalseWarnings() {
        val boundary = HomeStatus("0", "20", "unknown")
        assertFalse(boundary.hasNewMail)
        assertFalse(boundary.campusCardLow)
        assertFalse(boundary.networkEmpty)
    }
}
