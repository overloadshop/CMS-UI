package com.lindicra.cms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lindicra.cms.ui.theme.PrimaryText
import com.lindicra.cms.ui.theme.SecondaryText
import com.lindicra.cms.ui.theme.TertiaryFill

/** 单元格对齐方式。 */
enum class CMSTableAlign { Start, Center, End }

/**
 * 表格 · Table
 *
 * 与 [CMSCodeBlock] 同一套外观：可调圆角、最大高度 / 固定高度 / 无限高度、
 * 行分隔线与斑马纹，列宽不足时横向滚动，四边到边有阻尼回弹与轻触反馈。
 *
 * @param cornerRadius 0.dp 为直角
 * @param height 固定高度；内容超出时纵向滚动
 * @param maxHeight 最大高度；内容不足时按内容收缩
 * @param minColumnWidth 单列最小宽度；放不下时横向滚动
 * @param alignments 每列对齐；缺省按 Start
 */
@Composable
fun CMSTable(
    columns: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    height: Dp? = null,
    maxHeight: Dp? = null,
    minColumnWidth: Dp = 120.dp,
    cellPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 20.sp,
    surfaceColor: Color = TertiaryFill,
    headerColor: Color = PrimaryText,
    textColor: Color = PrimaryText,
    dividerColor: Color = SecondaryText.copy(alpha = 0.14f),
    showDividers: Boolean = true,
    striped: Boolean = true,
    stripeColor: Color = PrimaryText.copy(alpha = 0.028f),
    headerTint: Color = PrimaryText.copy(alpha = 0.02f),
    alignments: List<CMSTableAlign> = emptyList(),
    edgeFeedback: Boolean = true
) {
    val shape = RoundedCornerShape(cornerRadius)
    val bodyHeight = when {
        height != null -> Modifier.height(height)
        maxHeight != null -> Modifier.heightIn(max = maxHeight)
        else -> Modifier
    }
    BoxWithConstraints(modifier.clip(shape).background(surfaceColor)) {
        val columnCount = columns.size.coerceAtLeast(1)
        val cellWidth = maxOf(minColumnWidth, maxWidth / columnCount)
        val contentWidth = cellWidth * columnCount
        CMEdgeScrollBox(
            modifier = Modifier.fillMaxWidth().then(bodyHeight),
            horizontal = true,
            vertical = height != null || maxHeight != null,
            elastic = edgeFeedback
        ) {
            Column(Modifier.width(contentWidth)) {
                CMSTableRow(columns, contentWidth, cellWidth, header = true, color = headerColor,
                    background = headerTint, fontSize = fontSize, lineHeight = lineHeight,
                    cellPadding = cellPadding, alignments = alignments)
                if (showDividers) {
                    Box(Modifier.fillMaxWidth().height(0.6.dp).background(dividerColor))
                }
                rows.forEachIndexed { index, row ->
                    CMSTableRow(row, contentWidth, cellWidth, header = false, color = textColor,
                        background = if (striped && index % 2 == 1) stripeColor else Color.Transparent,
                        fontSize = fontSize, lineHeight = lineHeight,
                        cellPadding = cellPadding, alignments = alignments)
                    if (showDividers && index != rows.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(0.6.dp).background(dividerColor))
                    }
                }
            }
        }
    }
}

@Composable
private fun CMSTableRow(
    cells: List<String>,
    contentWidth: Dp,
    cellWidth: Dp,
    header: Boolean,
    color: Color,
    background: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    cellPadding: PaddingValues,
    alignments: List<CMSTableAlign>
) {
    Row(Modifier.width(contentWidth).background(background)) {
        repeat(if (cells.isEmpty()) 1 else cells.size) { column ->
            val align = alignments.getOrNull(column) ?: CMSTableAlign.Start
            Text(
                text = cells.getOrElse(column) { "" },
                color = color,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = when (align) {
                    CMSTableAlign.Start -> TextAlign.Start
                    CMSTableAlign.Center -> TextAlign.Center
                    CMSTableAlign.End -> TextAlign.End
                },
                modifier = Modifier.width(cellWidth).padding(cellPadding)
            )
        }
    }
}
