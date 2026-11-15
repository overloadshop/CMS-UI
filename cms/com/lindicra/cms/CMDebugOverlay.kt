package com.lindicra.cms

import android.app.ActivityManager
import android.content.Context
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.adamglin.composecontinuousroundedcornershape.ContinuousRoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 全局开发者调试覆盖层 — 显示 FPS / CPU / GPU / 温度 / 当前已知数值。
 * 通过 [DeveloperSettings.debugOverlayEnabled] 控制显隐。
 * 置于 Compose 树最顶层，不影响其他交互。
 */
@Composable
fun CMSDebugOverlay(modifier: Modifier = Modifier) {
    if (!DeveloperSettings.debugOverlayEnabled) return

    val context = LocalContext.current

    // ── FPS 统计 ──
    var fps by remember { mutableFloatStateOf(0f) }
    var frameCount by remember { mutableIntStateOf(0) }
    var lastFpsTime by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val now = System.currentTimeMillis()
                if (lastFpsTime == 0L) lastFpsTime = now
                frameCount++
                val elapsed = now - lastFpsTime
                if (elapsed >= 1000) {
                    fps = frameCount.toFloat() / (elapsed / 1000f)
                    frameCount = 0
                    lastFpsTime = now
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(callback)
        onDispose { Choreographer.getInstance().removeFrameCallback(callback) }
    }

    // ── 系统监控数据（每 2s 刷新） ──
    var cpuTemp by remember { mutableStateOf("--") }
    var cpuUsage by remember { mutableStateOf("--") }
    var gpuUsage by remember { mutableStateOf("--") }
    var memoryInfo by remember { mutableStateOf("--") }

    LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        while (true) {
            kotlinx.coroutines.delay(2000)
            val (temp, cpu, gpu, mem) = withContext(Dispatchers.IO) {
                readSystemStats(am)
            }
            cpuTemp = temp
            cpuUsage = cpu
            gpuUsage = gpu
            memoryInfo = mem
        }
    }

    // ── 渲染 ──
    val lineHeight = 13.sp
    val txtSize = 10.sp

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = buildString {
                appendLine("${"%.1f".format(fps)} FPS")
                appendLine("CPU $cpuUsage")
                if (gpuUsage != "--") appendLine("GPU $gpuUsage")
                appendLine("TEMP $cpuTemp")
                appendLine("MEM $memoryInfo")
                appendLine("${context.javaClass.simpleName}")
            },
            color = Color(0xCC00FF00),
            fontSize = txtSize,
            lineHeight = lineHeight,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(ContinuousRoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

/** 读取系统状态：温度 / CPU 占用 / GPU 占用 / 内存 */
private suspend fun readSystemStats(am: ActivityManager?): Array<String> = withContext(Dispatchers.IO) {
    val temp = readThermal()
    val cpu = readCpuUsage()
    val gpu = readGpuUsage()
    val mem = readMemoryInfo(am)
    arrayOf(temp, cpu, gpu, mem)
}

private fun readThermal(): String {
    // 扫描 thermal_zone（最高到 30），优先选 cpu/soc/tsens 类型
    for (i in 0..30) {
        try {
            val typeFile = File("/sys/class/thermal/thermal_zone$i/type")
            if (!typeFile.exists()) continue
            val type = typeFile.readText().trim().lowercase()
            if ("cpu" in type || "soc" in type || "tsens" in type || "back" in type || "xo" in type) {
                val raw = File("/sys/class/thermal/thermal_zone$i/temp").readText().trim()
                val v = raw.toIntOrNull() ?: continue
                if (v in 15000..120000) return "${v / 1000}.${(v % 1000) / 100}°C"
            }
        } catch (_: Exception) { continue }
    }
    // fallback: 返回第一个有效值
    for (i in 0..30) {
        try {
            val f = File("/sys/class/thermal/thermal_zone$i/temp")
            if (!f.exists()) continue
            val v = f.readText().trim().toIntOrNull() ?: continue
            if (v in 15000..120000) return "${v / 1000}.${(v % 1000) / 100}°C"
        } catch (_: Exception) { continue }
    }
    return "--"
}

private var prevCpuStat: LongArray? = null
private fun readCpuUsage(): String {
    try {
        val lines = File("/proc/stat").readLines()
        val parts = lines.firstOrNull()?.split("\\s+".toRegex()) ?: return "--"
        if (parts.size < 5) return "--"
        val user = parts[1].toLongOrNull() ?: return "--"
        val nice = parts[2].toLongOrNull() ?: 0L
        val sys = parts[3].toLongOrNull() ?: 0L
        val idle = parts[4].toLongOrNull() ?: 0L
        val total = user + nice + sys + idle

        val prev = prevCpuStat
        prevCpuStat = longArrayOf(user + nice + sys, idle, total)

        if (prev != null && prev.size == 3) {
            val deltaTotal = total - prev[2]
            val deltaBusy = (user + nice + sys) - prev[0]
            if (deltaTotal > 0) return "${(deltaBusy * 100 / deltaTotal)}%"
        }
        // 首次调用返回 idle 比率
        if (total > 0) return "${((total - idle) * 100 / total)}%"
    } catch (_: Exception) { }
    return "--"
}

private fun readGpuUsage(): String {
    // 高通 Adreno
    val paths = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpubusy",
        "/sys/class/kgsl/kgsl-3d0/busy_time",
        "/sys/class/kgsl/kgsl-3d0/utilization"
    )
    for (p in paths) {
        try {
            val f = File(p)
            if (!f.exists()) continue
            val raw = f.readText().trim()
            val parts = raw.split("\\s+".toRegex())
            if (parts.size >= 2) {
                val num = parts[0].toLongOrNull() ?: continue
                val den = parts[1].toLongOrNull() ?: continue
                if (den > 0) return "${(num * 100 / den)}%"
            } else {
                val v = parts[0].toIntOrNull()
                if (v != null && v in 0..100) return "${v}%"
                return raw.take(20)
            }
        } catch (_: Exception) { continue }
    }
    // 联发科 GPU
    try {
        val f = File("/proc/gpu_utilization")
        if (f.exists()) {
            val v = f.readText().trim().toIntOrNull()
            if (v != null && v in 0..100) return "${v}%"
        }
    } catch (_: Exception) { }
    return "--"
}

private fun readMemoryInfo(am: ActivityManager?): String {
    // 优先 ActivityManager（权限兼容）
    try {
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        if (mi.totalMem > 0) {
            val usedPct = ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt()
            return "${usedPct}% (${mi.availMem / 1048576}/${mi.totalMem / 1048576}MB)"
        }
    } catch (_: Exception) { }
    // fallback: /proc/meminfo
    try {
        val lines = File("/proc/meminfo").readLines()
        var total = 0L; var available = 0L
        for (line in lines) {
            when {
                line.startsWith("MemTotal:") -> total = line.replace("\\D+".toRegex(), "").toLongOrNull() ?: 0L
                line.startsWith("MemAvailable:") -> available = line.replace("\\D+".toRegex(), "").toLongOrNull() ?: 0L
            }
        }
        if (total > 0) {
            val usedPct = ((total - available) * 100 / total).toInt()
            return "${usedPct}% (${available / 1024}/${total / 1024}MB)"
        }
    } catch (_: Exception) { }
    return "--"
}
