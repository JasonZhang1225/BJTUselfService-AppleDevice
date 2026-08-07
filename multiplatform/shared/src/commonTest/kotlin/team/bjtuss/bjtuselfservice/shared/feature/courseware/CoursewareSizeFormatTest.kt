package team.bjtuss.bjtuselfservice.shared.feature.courseware

import kotlin.test.Test
import kotlin.test.assertEquals

class CoursewareSizeFormatTest {
    @Test
    fun bareNumberGetsMbUnit() {
        assertEquals("5.34 MB", formatCoursewareSize("5.34"))
        assertEquals("8 MB", formatCoursewareSize("8"))
    }

    @Test
    fun keepsExistingUnit() {
        assertEquals("5.34 MB", formatCoursewareSize("5.34 MB"))
        assertEquals("512 KB", formatCoursewareSize("512 KB"))
    }

    @Test
    fun blankStaysBlank() {
        assertEquals("", formatCoursewareSize(""))
        assertEquals("", formatCoursewareSize("   "))
    }

    @Test
    fun typeLabelsAreHumanReadable() {
        assertEquals("RAR 压缩文件", formatCoursewareTypeLabel("rar"))
        assertEquals("PPT 演示文件", formatCoursewareTypeLabel("ppt"))
        assertEquals("PPTX 演示文件", formatCoursewareTypeLabel("pptx"))
        assertEquals("PDF 文档", formatCoursewareTypeLabel("pdf"))
        assertEquals("DOCX 文档", formatCoursewareTypeLabel("docx"))
        assertEquals("XLSX 表格", formatCoursewareTypeLabel("xlsx"))
        assertEquals("MP4 视频", formatCoursewareTypeLabel("mp4"))
        assertEquals("PNG 图片", formatCoursewareTypeLabel("png"))
        assertEquals("RAR", formatCoursewareTypeBadge("rar"))
        assertEquals("未知类型", formatCoursewareTypeLabel(""))
    }
}
