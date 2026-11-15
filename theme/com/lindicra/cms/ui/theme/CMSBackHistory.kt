package com.lindicra.cms.ui.theme

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/** 返回历史中的一个页面。 */
data class CMSBackDestination(
    /** 宿主定义的路由/页面标识，用于 [CMSBackHistory.returnTo]。 */
    val id: String,
    /** 菜单中展示的标题。 */
    val label: String
)

/**
 * 返回历史 · Back History 宿主接口（1.1.0）
 *
 * [com.lindicra.cms.CMSGlassBackButton] 长按时通过它取历史页面并跳转。
 * 通过 CMS.Interaction.BackHistory 或主题 backHistory 参数提供。
 * 宿主不提供实现时，长按不弹菜单，短按返回照常工作。
 */
interface CMSBackHistory {

    /** 当前栈中已访问且可回退的页面，不含当前页，最新的在前。 */
    fun previous(context: Context): List<CMSBackDestination>

    /** 回到指定页面，通常会关闭中间页面。 */
    fun returnTo(context: Context, id: String)

    /** 页面标题变化时同步到历史栈，菜单里显示最新标题。 */
    fun updateTitle(context: Context, title: String)
}

/** 当前返回历史实现；未提供时返回按钮退化为普通返回。 */
val LocalCMSBackHistory = staticCompositionLocalOf<CMSBackHistory?> { null }
