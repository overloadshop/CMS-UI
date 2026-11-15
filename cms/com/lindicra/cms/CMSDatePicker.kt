package com.lindicra.cms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lindicra.cms.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth

enum class CMSWeekStart { Monday, Sunday }
enum class CMSDatePickerMode { Calendar, Wheel, Input }
private enum class CalendarLevel { Days, Months, Years }

internal fun pickerDateInRange(date: LocalDate, lower: LocalDate, upper: LocalDate): LocalDate =
    when {
        date.isBefore(lower) -> lower
        date.isAfter(upper) -> upper
        else -> date
    }

internal fun pickerDateWithMonth(date: LocalDate, year: Int, month: Int): LocalDate {
    val target = YearMonth.of(year, month)
    return target.atDay(date.dayOfMonth.coerceAtMost(target.lengthOfMonth()))
}

/** 日历 / 年月日滚筒 / 直接输入，共用同一日期范围和受控选中值。 */
@Composable
fun CMSDatePicker(
    selected: LocalDate,
    onSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minYear: Int = 1970,
    maxYear: Int = 2100,
    weekStart: CMSWeekStart = CMSWeekStart.Monday,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    showTodayButton: Boolean = true,
    mode: CMSDatePickerMode = CMSDatePickerMode.Calendar,
    showModeSwitch: Boolean = false
) {
    val requestedLower = minDate ?: LocalDate.of(minYear.coerceIn(1, 9999), 1, 1)
    val requestedUpper = maxDate ?: LocalDate.of(maxYear.coerceIn(1, 9999), 12, 31)
    // 宿主配置错误不应让整个页面崩溃：交换反向范围，并把年份夹在
    // java.time 支持的 1..9999 内。
    val lower = minOf(requestedLower, requestedUpper)
    val upper = maxOf(requestedLower, requestedUpper)
    val value = pickerDateInRange(selected, lower, upper)
    var currentMode by remember(mode) { mutableStateOf(mode) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(value)) }
    var level by remember { mutableStateOf(CalendarLevel.Days) }
    val lowerMonth = YearMonth.from(lower)
    val upperMonth = YearMonth.from(upper)
    val today = LocalDate.now()
    val haptic = rememberCMSHaptic()
    var dateText by remember(value) { mutableStateOf(value.toString()) }
    val typedDate = remember(dateText) { runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() }
    val typedDateValid = typedDate != null &&
        !typedDate.isBefore(lower) && !typedDate.isAfter(upper)
    LaunchedEffect(value, lower, upper) { visibleMonth = YearMonth.from(value) }
    LaunchedEffect(currentMode) { level = CalendarLevel.Days }
    fun choose(date: LocalDate, feedback: Boolean = true) {
        val next = pickerDateInRange(date, lower, upper)
        if (next != selected) {
            if (feedback) haptic(CMSHapticKind.Tick)
            onSelected(next)
        }
    }
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showModeSwitch) {
            CMSSegmentedControl(listOf("日历", "滚筒", "输入"), currentMode.ordinal,
                { CMSDatePickerMode.entries.getOrNull(it)?.let { mode -> currentMode = mode } })
        }
        if (currentMode == CMSDatePickerMode.Calendar) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CMSTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    modifier = Modifier.weight(1f),
                    label = "日期",
                    placeholder = "YYYY-MM-DD",
                    isError = dateText.isNotEmpty() && !typedDateValid,
                    supportingText = null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii
                    )
                )
                DateAction("应用", typedDateValid) {
                    typedDate?.let { choose(it) }
                }
            }
            if (showTodayButton) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DateAction("今天", !today.isBefore(lower) && !today.isAfter(upper)) {
                        choose(today)
                        visibleMonth = YearMonth.from(pickerDateInRange(today, lower, upper))
                        level = CalendarLevel.Days
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${value.year} 年 ${value.monthValue} 月 ${value.dayOfMonth} 日",
                    color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    fontFamily = HarmonySansSC, style = CMSNoFontPadding)
                if (showTodayButton) {
                    DateAction("今天", !today.isBefore(lower) && !today.isAfter(upper)) {
                        choose(today)
                        visibleMonth = YearMonth.from(pickerDateInRange(today, lower, upper))
                        level = CalendarLevel.Days
                    }
                }
            }
        }
        when (currentMode) {
            CMSDatePickerMode.Wheel -> {
                val years = remember(lower.year, upper.year) { (lower.year..upper.year).toList() }
                val firstMonth = if (value.year == lower.year) lower.monthValue else 1
                val lastMonth = if (value.year == upper.year) upper.monthValue else 12
                val months = (firstMonth..lastMonth).toList()
                val firstDay = if (YearMonth.from(value) == lowerMonth) lower.dayOfMonth else 1
                val lastDay = if (YearMonth.from(value) == upperMonth) upper.dayOfMonth
                    else YearMonth.from(value).lengthOfMonth()
                val days = (firstDay..lastDay).toList()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CMSWheelPicker(years.map { "${it}年" }, (value.year - lower.year).coerceIn(years.indices),
                        { index -> years.getOrNull(index)?.let {
                            choose(pickerDateWithMonth(value, it, value.monthValue), false)
                        } },
                        Modifier.weight(1.3f))
                    CMSWheelPicker(months.map { "${it}月" }, (value.monthValue - firstMonth).coerceIn(months.indices),
                        { index -> months.getOrNull(index)?.let {
                            choose(pickerDateWithMonth(value, value.year, it), false)
                        } },
                        Modifier.weight(1f))
                    CMSWheelPicker(days.map { "${it}日" }, (value.dayOfMonth - firstDay).coerceIn(days.indices),
                        { index -> days.getOrNull(index)?.let { choose(value.withDayOfMonth(it), false) } },
                        Modifier.weight(1f))
                }
            }
            CMSDatePickerMode.Input -> {
                var text by remember(value) { mutableStateOf(value.toString()) }
                val parsed = runCatching { LocalDate.parse(text.trim()) }.getOrNull()
                val valid = parsed != null && !parsed.isBefore(lower) && !parsed.isAfter(upper)
                CMSPickerInput("日期 · YYYY-MM-DD", text, { text = it }, error = !valid,
                    keyboardType = KeyboardType.Text)
                Text(
                    if (valid) "可选范围：$lower 至 $upper"
                    else if (parsed == null) "请输入有效日期，例如 2026-11-11"
                    else "日期必须在 $lower 至 $upper 之间",
                    color = if (valid) SecondaryText else ColorError,
                    fontSize = 12.sp, fontFamily = HarmonySansSC, style = CMSNoFontPadding)
                DateAction("应用日期", valid) { parsed?.let { choose(it) } }
            }
            CMSDatePickerMode.Calendar -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateAction("‹", level != CalendarLevel.Years && visibleMonth > lowerMonth) {
                        val previous = visibleMonth.minusMonths(if (level == CalendarLevel.Days) 1 else 12)
                        visibleMonth = if (previous < lowerMonth) lowerMonth else previous
                        haptic(CMSHapticKind.Tick)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                        DateAction("${visibleMonth.year} 年") { level = CalendarLevel.Years }
                        DateAction("${visibleMonth.monthValue} 月") { level = CalendarLevel.Months }
                    }
                    DateAction("›", level != CalendarLevel.Years && visibleMonth < upperMonth) {
                        val next = visibleMonth.plusMonths(if (level == CalendarLevel.Days) 1 else 12)
                        visibleMonth = if (next > upperMonth) upperMonth else next
                        haptic(CMSHapticKind.Tick)
                    }
                }
                when (level) {
                    CalendarLevel.Years -> {
                        val years = remember(lower.year, upper.year) { (lower.year..upper.year).toList() }
                        val grid = rememberLazyGridState(
                            initialFirstVisibleItemIndex = ((visibleMonth.year - lower.year) / 4) * 4)
                        LazyVerticalGrid(GridCells.Fixed(4), state = grid,
                            modifier = Modifier.fillMaxWidth().height(264.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(years.size, key = { years.getOrNull(it) ?: lower.year }) { index ->
                                val year = years.getOrNull(index) ?: return@items
                                DateCell("$year", year == visibleMonth.year, false, true,
                                    Modifier.height(44.dp)) {
                                    visibleMonth = YearMonth.from(pickerDateInRange(
                                        pickerDateWithMonth(value, year, visibleMonth.monthValue), lower, upper))
                                    level = CalendarLevel.Months
                                    haptic(CMSHapticKind.Tick)
                                }
                            }
                        }
                    }
                    CalendarLevel.Months -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(4) { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    repeat(3) { column ->
                                        val month = row * 3 + column + 1
                                        val target = YearMonth.of(visibleMonth.year, month)
                                        DateCell("${month}月", target == visibleMonth, false,
                                            target >= lowerMonth && target <= upperMonth,
                                            Modifier.weight(1f).height(58.dp)) {
                                            visibleMonth = target
                                            level = CalendarLevel.Days
                                            haptic(CMSHapticKind.Tick)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    CalendarLevel.Days -> {
                        val labels = if (weekStart == CMSWeekStart.Monday)
                            listOf("一", "二", "三", "四", "五", "六", "日")
                        else listOf("日", "一", "二", "三", "四", "五", "六")
                        Row(Modifier.fillMaxWidth()) {
                            labels.forEach {
                                Text(it, Modifier.weight(1f), color = SecondaryText,
                                    fontSize = 12.sp, fontFamily = HarmonySansSC, textAlign = TextAlign.Center)
                            }
                        }
                        val first = visibleMonth.atDay(1)
                        val leading = if (weekStart == CMSWeekStart.Monday) first.dayOfWeek.value - 1
                            else first.dayOfWeek.value % 7
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(6) { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    repeat(7) { column ->
                                        val day = row * 7 + column - leading + 1
                                        if (day !in 1..visibleMonth.lengthOfMonth()) {
                                            Box(Modifier.weight(1f).height(44.dp))
                                        } else {
                                            val date = visibleMonth.atDay(day)
                                            DateCell("$day", date == value, date == today,
                                                date >= lower && date <= upper,
                                                Modifier.weight(1f).height(44.dp).semantics {
                                                    contentDescription = "${date.year}年${date.monthValue}月${day}日"
                                                }) { choose(date) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (level != CalendarLevel.Days) {
                    DateAction("返回日历") { level = CalendarLevel.Days }
                }
            }
        }
    }
}

private val ColorError = androidx.compose.ui.graphics.Color(0xFFEF4444)

@Composable
private fun DateAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
        .background(TertiaryFill)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) LocalCMSAccentColor.current else TertiaryText,
            fontSize = 13.sp, fontFamily = HarmonySansSC, style = CMSNoFontPadding)
    }
}

@Composable
private fun DateCell(
    text: String, isSelected: Boolean, today: Boolean, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val accent = LocalCMSAccentColor.current
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.clip(shape)
        .background(if (isSelected) accent else androidx.compose.ui.graphics.Color.Transparent)
        .then(if (today && !isSelected) Modifier.border(1.dp, accent, shape) else Modifier)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { selected = isSelected }, contentAlignment = Alignment.Center) {
        Text(text, color = when {
            !enabled -> TertiaryText.copy(alpha = 0.5f)
            isSelected -> androidx.compose.ui.graphics.Color.White
            today -> accent
            else -> PrimaryText
        }, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            fontFamily = HarmonySansSC, style = CMSNoFontPadding)
    }
}
