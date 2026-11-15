package com.lindicra.cms.example

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import com.lindicra.cms.CMS
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import dev.chrisbanes.haze.HazeState
import com.lindicra.cms.ui.theme.*
import java.util.UUID

private const val CONFIGURE = 1
private const val CLOSE = 2
private const val CHANGED = 3
private const val SHOWN = 4
private const val CLOSED = 5
private const val RETURN_TO = 6

private fun payload(session: String, page: DemoPage, values: Map<String, String>, revision: Long,
    history: List<CMSBackDestination>) =
    Bundle().apply {
        putString("session", session)
        putString("page", page.name)
        putLong("revision", revision)
        putStringArrayList("historyIds", ArrayList(history.map { it.id }))
        putStringArrayList("historyLabels", ArrayList(history.map { it.label }))
        putBundle("values", Bundle().apply { values.forEach { (key, value) -> putString(key, value) } })
    }

private data class RemoteSnapshot(
    val session: String, val page: DemoPage, val values: Map<String, String>, val revision: Long,
    val history: List<CMSBackDestination>
)

private fun Bundle.snapshot(): RemoteSnapshot {
    val page = DemoPage.from(getString("page"))
    val definition = PreviewDefinition(page)
    val raw = requireNotNull(getBundle("values"))
    val values = definition.fields.associate { field ->
        field.name to field.normalize(requireNotNull(raw.getString(field.name)))
    }
    definition.validateConstraints(values)
    val ids = getStringArrayList("historyIds").orEmpty()
    val labels = getStringArrayList("historyLabels").orEmpty()
    require(ids.size == labels.size && ids.distinct().size == ids.size)
    return RemoteSnapshot(requireNotNull(getString("session")), page, values, getLong("revision"),
        ids.zip(labels) { id, label -> CMSBackDestination(id, label) })
}

/** No static editor state crosses processes; every message carries a session and revision. */
internal class PreviewConnection(private val context: Context, private val page: DemoPage) {
    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf("未打开")
        private set
    var onValues: ((Map<String, String>) -> Unit)? = null
    private var session = ""
    private var revision = 0L
    private var values: Map<String, String> = emptyMap()
    private var remote: Messenger? = null
    private var binding: ServiceConnection? = null
    private var pendingReset = false
    private val callback = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.data.getString("session") == session && running) {
            when (message.what) {
                SHOWN -> status = "已打开 · 参数实时同步"
                CLOSED -> close()
                RETURN_TO -> {
                    val id = message.data.getString("destination")
                    if (ExampleBackHistory.includingCurrent(context).any { it.id == id }) {
                        ExampleBackHistory.returnTo(context, requireNotNull(id))
                    }
                }
                CHANGED -> if (message.data.getLong("revision") == revision) {
                    runCatching { message.data.snapshot() }.onSuccess { received ->
                        values = received.values
                        onValues?.invoke(values)
                    }
                }
            }
        }
        true
    })

    fun sync(updated: Map<String, String>) {
        if (updated == values) return
        values = updated.toMap()
        revision++
        if (running && remote != null) sendConfiguration()
    }

    fun open(updated: Map<String, String>, reset: Boolean = false) {
        sync(updated)
        pendingReset = reset
        if (remote != null) {
            if (reset) session = UUID.randomUUID().toString()
            sendConfiguration()
            launch()
            return
        }
        if (binding != null) return
        session = UUID.randomUUID().toString()
        running = true
        status = "正在打开"
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (binding !== this) return
                remote = Messenger(binder)
                sendConfiguration()
                launch()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (binding === this) {
                    close()
                    status = "预览已退出，可重新打开"
                }
            }
            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
            override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
        }
        binding = connection
        runCatching {
            check(context.bindService(Intent(context, PreviewHostService::class.java),
                connection, Context.BIND_AUTO_CREATE)) { "无法连接预览" }
        }.onFailure { close(); status = "打开失败：${it.message}" }
    }

    private fun sendConfiguration() {
        send(CONFIGURE, payload(session, page, values, revision,
            ExampleBackHistory.includingCurrent(context)))
    }

    private fun send(what: Int, data: Bundle): Boolean = runCatching {
        requireNotNull(remote).send(Message.obtain(null, what).apply {
            this.data = data
            replyTo = this@PreviewConnection.callback
        })
    }.onFailure { close(); status = "预览连接已断开" }.isSuccess

    private fun launch() {
        if (!running) return
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            if (pendingReset) Intent.FLAG_ACTIVITY_CLEAR_TASK else Intent.FLAG_ACTIVITY_SINGLE_TOP
        runCatching {
            context.startActivity(Intent(context, RemotePreviewActivity::class.java)
                .addFlags(flags).putExtras(payload(session, page, values, revision,
                    ExampleBackHistory.includingCurrent(context))))
            pendingReset = false
        }.onFailure { close(); status = "打开失败：${it.message}" }
    }

    fun close() {
        val messenger = remote
        remote = null
        running = false
        status = "已关闭"
        runCatching {
            messenger?.send(Message.obtain(null, CLOSE).apply {
                data = Bundle().apply { putString("session", session) }
            })
        }
        val connection = binding
        binding = null
        if (connection != null) runCatching { context.unbindService(connection) }
    }
}

/** Shared only by the service and Activity inside :preview. */
private object PreviewHost {
    var snapshot by mutableStateOf<RemoteSnapshot?>(null)
    var activity: RemotePreviewActivity? = null
    var editor: Messenger? = null

    fun configure(next: RemoteSnapshot) {
        val previous = snapshot
        if (previous?.session != next.session || next.revision >= previous.revision) snapshot = next
    }

    fun notify(what: Int, destination: String? = null) {
        val current = snapshot ?: return
        runCatching {
            editor?.send(Message.obtain(null, what).apply {
                data = payload(current.session, current.page, current.values, current.revision,
                    current.history).apply { putString("destination", destination) }
            })
        }.onFailure { close() }
    }

    fun edit(name: String, value: String) {
        val current = snapshot ?: return
        val definition = PreviewDefinition(current.page)
        val next = current.values.toMutableMap().apply {
            put(name, definition.fields.first { it.name == name }.normalize(value))
        }
        definition.validateConstraints(next)
        snapshot = current.copy(values = next)
        notify(CHANGED)
    }

    fun close() {
        activity?.finishAndRemoveTask()
        activity = null
        snapshot = null
        editor = null
    }
}

class PreviewHostService : Service() {
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            CONFIGURE -> runCatching {
                PreviewHost.configure(message.data.snapshot())
                PreviewHost.editor = message.replyTo
                if (PreviewHost.activity != null) PreviewHost.notify(SHOWN)
            }.onFailure { PreviewHost.close() }
            CLOSE -> if (message.data.getString("session") == PreviewHost.snapshot?.session) {
                PreviewHost.close()
            }
        }
        true
    })
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onUnbind(intent: Intent): Boolean {
        PreviewHost.close()
        return false
    }
    override fun onDestroy() {
        PreviewHost.close()
        super.onDestroy()
    }
}

/** Separate task allows returning to the editor while keeping this instance open. */
class RemotePreviewActivity : ComponentActivity() {
    private var currentSession: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        if (!accept(intent)) return
        setContent {
            val snapshot = PreviewHost.snapshot
            if (snapshot != null) key(snapshot.session) {
                val values = PreviewValues(snapshot.values)
                val haze = remember { HazeState() }
                val dark = when (values.int("appearance")) {
                    1 -> false
                    2 -> true
                    else -> isSystemInDarkTheme()
                }
                val history = remember {
                    object : CMSBackHistory {
                        override fun previous(context: Context) =
                            PreviewHost.snapshot?.history.orEmpty()
                        override fun returnTo(context: Context, id: String) {
                            if (previous(context).any { it.id == id }) {
                                PreviewHost.notify(RETURN_TO, id)
                                returnToEditor()
                            }
                        }
                        override fun updateTitle(context: Context, title: String) {}
                    }
                }
                CMS.Appearance.Theme(darkTheme = dark, hazState = haze,
                    glassMaterial = GlassMaterial(values.bool("advanced"), values.int("clarity")),
                    hapticsEnabled = values.bool("hapticsEnabled"), backHistory = history,
                    accentColor = values.color("themeColor")) {
                    ApplySystemBars(dark, window)
                    FullScreenPreviewFrame(snapshot.page, haze, ::returnToEditor,
                        showTopBar = values.bool("previewTopBar")) {
                        ComponentPreview(snapshot.page, values, haze, ::returnToEditor,
                            fullScreen = true) { name, value ->
                            PreviewHost.edit(name, value)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
    }

    private fun accept(intent: Intent): Boolean = runCatching {
        val next = requireNotNull(intent.extras).snapshot()
        currentSession = next.session
        PreviewHost.configure(next)
        PreviewHost.activity = this
        PreviewHost.notify(SHOWN)
    }.onFailure { finishAndRemoveTask() }.isSuccess

    private fun returnToEditor() { moveTaskToBack(true) }

    override fun onDestroy() {
        if (PreviewHost.activity === this) {
            PreviewHost.activity = null
            if (isFinishing && currentSession == PreviewHost.snapshot?.session) {
                PreviewHost.notify(CLOSED)
            }
        }
        super.onDestroy()
    }
}
