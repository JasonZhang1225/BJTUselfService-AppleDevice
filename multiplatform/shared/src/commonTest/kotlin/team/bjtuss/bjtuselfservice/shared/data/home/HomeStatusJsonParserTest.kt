package team.bjtuss.bjtuselfservice.shared.data.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomeStatusJsonParserTest {
    @Test fun parsesObservedStringShape() {
        val result = assertIs<HomeStatusParseResult.Success>(
            parseHomeStatusJson("""{"net_fee":"12.50","ecard_yuer":"88.00","newmail_count":"2"}"""),
        )
        assertEquals("2", result.status.newMailCount)
        assertEquals("88.00", result.status.campusCardBalance)
        assertEquals("12.50", result.status.networkBalance)
    }

    @Test fun acceptsJsonNumbersWithoutChangingDisplayText() {
        val result = assertIs<HomeStatusParseResult.Success>(
            parseHomeStatusJson("""{"net_fee":0,"ecard_yuer":19.5,"newmail_count":3}"""),
        )
        assertEquals("19.5", result.status.campusCardBalance)
    }

    @Test fun rejectsMissingAndMalformedFields() {
        assertIs<HomeStatusParseResult.Failure>(parseHomeStatusJson("{}"))
        assertIs<HomeStatusParseResult.Failure>(parseHomeStatusJson("not-json"))
    }
}
