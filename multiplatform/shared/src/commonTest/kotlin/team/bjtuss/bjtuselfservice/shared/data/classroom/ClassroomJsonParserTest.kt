package team.bjtuss.bjtuselfservice.shared.data.classroom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ClassroomJsonParserTest {
    @Test
    fun parsesRealisticMixedUsageValues() {
        val result = assertIs<ClassroomJsonParseResult.Success>(
            parseClassroomCapacityJson(
                "思源楼",
                """{"time":["2026-07-30 19:54:01","2026-07-30 19:54:37"],"data":[["SY101",0.0,0,90],["SY102",100.0,32,32],["SY105",0.92,1,108]]}""",
            ),
        )
        assertEquals("思源楼", result.info.buildingName)
        assertEquals(3, result.info.classrooms.size)
        assertEquals(0.92, result.info.classrooms.last().usagePercent)
        assertEquals(108, result.info.classrooms.last().capacity)
    }

    @Test
    fun acceptsEmptyClassroomList() {
        val result = assertIs<ClassroomJsonParseResult.Success>(
            parseClassroomCapacityJson("思源楼", """{"time":["a","b"],"data":[]}"""),
        )
        assertEquals(emptyList(), result.info.classrooms)
    }

    @Test
    fun rejectsMissingTime() {
        val result = assertIs<ClassroomJsonParseResult.Failure>(
            parseClassroomCapacityJson("思源楼", """{"data":[]}"""),
        )
        assertEquals("time", result.field)
    }

    @Test
    fun rejectsShortClassroomRow() {
        val result = assertIs<ClassroomJsonParseResult.Failure>(
            parseClassroomCapacityJson("思源楼", """{"time":["a","b"],"data":[["SY101",0.0,0]]}"""),
        )
        assertEquals("data[0]", result.field)
    }

    @Test
    fun rejectsStringNumberWithoutLeakingBody() {
        val result = assertIs<ClassroomJsonParseResult.Failure>(
            parseClassroomCapacityJson(
                "思源楼",
                """{"time":["a","b"],"data":[["敏感教室","100",1,2]]}""",
            ),
        )
        assertEquals("data[0][1]", result.field)
        assertEquals(false, result.toString().contains("敏感教室"))
    }
}
