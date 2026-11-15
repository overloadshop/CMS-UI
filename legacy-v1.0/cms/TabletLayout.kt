package ldyc.qy.music

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 平板判定阈值：最短边 ≥ 600dp（对齐鸿蒙 isLargeViewportForSplitLayout） */
const val TABLET_MIN_DP = 600

/**
 * 屏幕面积缩放系数 — 大屏（平板）按物理屏幕面积等比放大，手机恒为 1.0 不变。
 *
 * 标定基准：vivo Pad3 Pro（3096×2064，13"）应放大 40% → scale = 1.4。
 *   sqrt(3096×2064) ≈ 2527.9 → BASE_SQRT_AREA = 2527.9 / 1.4 ≈ 1805.64
 *   典型手机 1080×2400 → sqrt ≈ 1610 < 基准 → clamp 到 1.0（手机不缩放）
 *
 * 计算时机：与进入主页同步（HomePage 首帧调用 [ensure]），不在启动路径计算，
 * 保证极致启动速度。结果按"屏幕面积"索引写入 SharedPreferences：再次启动读到
 * 匹配面积的数据就直接复用，不重复计算；换屏（面积变化）才重新计算。
 */
object ScreenScale {
    private const val PREFS = "screen_scale"
    private const val KEY_AREA = "screen_area"
    private const val KEY_SCALE = "scale"
    // sqrt(3096 * 2064) / 1.4
    private const val BASE_SQRT_AREA = 1805.64f

    @Volatile
    var cached: Float = 1f
        private set

    private var measuredArea = -1L

    /** 惰性计算并缓存：本会话已算过 / 本地有匹配面积的数据 → 直接复用；否则计算并落盘 */
    fun ensure(context: Context) {
        val m = context.resources.displayMetrics
        val area = m.widthPixels.toLong() * m.heightPixels
        if (area == measuredArea) return
        synchronized(this) {
            if (area == measuredArea) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            cached = if (prefs.getLong(KEY_AREA, 0L) == area) {
                prefs.getFloat(KEY_SCALE, 1f)
            } else {
                compute(m.widthPixels, m.heightPixels).also {
                    prefs.edit().putLong(KEY_AREA, area).putFloat(KEY_SCALE, it).apply()
                }
            }
            measuredArea = area
        }
    }

    private fun compute(w: Int, h: Int): Float {
        val area = w.toLong() * h
        return max(1f, sqrt(area.toDouble()).toFloat() / BASE_SQRT_AREA)
    }
}

/** 当前屏幕缩放系数（未 [ensure] 时为 1.0；手机恒 1.0） */
@Composable
fun screenScaleFactor(): Float = ScreenScale.cached

/**
 * 平板内容区最大宽度：横竖屏都充分利用屏幕宽度（仅左右留 24dp 边距），
 * 不固定死宽度 —— 横屏平板内容区自然更宽，多列网格随之填满
 */
@Composable
fun tabletContentMaxWidth(): Dp {
    val w = LocalConfiguration.current.screenWidthDp
    return if (w >= TABLET_MIN_DP) (w - 48).dp else Dp.Unspecified
}

/**
 * 当前是否平板（大屏）— 不止靠当前朝向的宽度：
 * 1. smallestScreenWidthDp ≥ 600（Android 官方大屏判定标准，横竖屏都稳定，不随朝向翻转漂移）
 * 2. UiModeManager 形态 = 电视等固定大屏
 */
@Composable
fun isTabletScreen(): Boolean {
    val config = LocalConfiguration.current
    if (config.smallestScreenWidthDp >= TABLET_MIN_DP) return true
    val uiMode = LocalContext.current.getSystemService(UiModeManager::class.java)
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/** 按当前屏幕宽度返回网格列数：横屏平板（≥1000）3 列，竖屏平板（≥600）2 列，手机 1 列 */
@Composable
fun gridColumnsForWidth(): Int {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= 1000 -> 3
        w >= TABLET_MIN_DP -> 2
        else -> 1
    }
}

/**
 * 平板多列网格：把 [itemCount] 个条目按 [columns] 列分行，每行横向填满（weight 均分），
 * 列间用 [columnSpacing] 留间隔。用于单列组件被拉得很长时的平板适配，手机传 columns=1 即回到原单列。
 */
@Composable
fun ColumnScope.gridRows(
    itemCount: Int,
    columns: Int,
    rowSpacing: Dp = 0.dp,
    columnSpacing: Dp = 0.dp,
    item: @Composable (index: Int) -> Unit
) {
    if (itemCount <= 0) return
    val cols = columns.coerceAtLeast(1)
    var first = 0
    while (first < itemCount) {
        val last = min(first + cols, itemCount)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(columnSpacing)
        ) {
            for (i in first until last) {
                Box(Modifier.weight(1f)) { item(i) }
            }
            repeat(cols - (last - first)) { Spacer(Modifier.weight(1f)) }
        }
        first = last
        if (first < itemCount) Spacer(Modifier.height(rowSpacing))
    }
}
