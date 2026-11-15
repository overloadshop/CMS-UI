package com.lindicra.cms.example

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.lindicra.cms.CMS
import com.lindicra.cms.ui.theme.CMSBackDestination
import com.lindicra.cms.ui.theme.CMSBackHistory
import java.lang.ref.WeakReference
import java.util.UUID

/** Each visited Activity is a distinct destination, including repeated document screens. */
abstract class ExampleActivity : ComponentActivity() {
    internal var historyId = ""
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyId = savedInstanceState?.getString("cms.history.id") ?: UUID.randomUUID().toString()
        ExampleBackHistory.register(this, savedInstanceState?.getString("cms.history.title"))
    }

    protected fun setExampleContent(content: @Composable () -> Unit) {
        setContent { CMS.Interaction.BackHistory(ExampleBackHistory, content) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("cms.history.id", historyId)
        outState.putString("cms.history.title", ExampleBackHistory.title(this))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing) ExampleBackHistory.remove(this)
        super.onDestroy()
    }
}

internal object ExampleBackHistory : CMSBackHistory {
    private data class Entry(
        val id: String, val task: Int, var label: String,
        var activity: WeakReference<ExampleActivity>
    )
    private val entries = mutableListOf<Entry>()

    private fun activity(context: Context): ExampleActivity? = when (context) {
        is ExampleActivity -> context
        is ContextWrapper -> if (context.baseContext !== context) activity(context.baseContext) else null
        else -> null
    }

    fun register(activity: ExampleActivity, savedTitle: String?) {
        val existing = entries.firstOrNull { it.id == activity.historyId }
        if (existing != null) {
            existing.activity = WeakReference(activity)
        } else {
            entries += Entry(activity.historyId, activity.taskId, savedTitle ?: "CMS 示例",
                WeakReference(activity))
        }
    }

    fun remove(activity: ExampleActivity) {
        entries.removeAll { it.id == activity.historyId && it.activity.get() === activity }
    }

    fun title(activity: ExampleActivity) = entries.firstOrNull { it.id == activity.historyId }?.label

    private fun stack(context: Context): List<Entry> {
        val current = activity(context) ?: return emptyList()
        val task = entries.filter { it.task == current.taskId }
        val index = task.indexOfFirst { it.id == current.historyId }
        if (index < 0) return emptyList()
        return task.take(index + 1).filter {
            it.activity.get()?.let { host -> !host.isFinishing && !host.isDestroyed } == true
        }
    }

    override fun previous(context: Context) = stack(context).dropLast(1).asReversed()
        .map { CMSBackDestination(it.id, it.label) }

    /** Remote preview includes its editor as the nearest return destination. */
    fun includingCurrent(context: Context) = stack(context).asReversed()
        .map { CMSBackDestination(it.id, it.label) }

    override fun returnTo(context: Context, id: String) {
        val current = activity(context) ?: return
        val task = entries.filter { it.task == current.taskId }
        val currentIndex = task.indexOfFirst { it.id == current.historyId }
        val targetIndex = task.indexOfFirst { it.id == id }
        if (targetIndex !in 0..currentIndex) return
        val target = task[targetIndex].activity.get() ?: return
        if (target.isFinishing || target.isDestroyed) return
        // Finish actual instances; CLEAR_TOP would pick the wrong repeated PreviewActivity.
        task.subList(targetIndex + 1, task.size).asReversed().forEach { entry ->
            entry.activity.get()?.finish()
        }
    }

    override fun updateTitle(context: Context, title: String) {
        val host = activity(context) ?: return
        entries.firstOrNull { it.id == host.historyId }?.label = title
    }
}
