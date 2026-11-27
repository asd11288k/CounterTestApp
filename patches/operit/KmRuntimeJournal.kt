package com.ai.assistance.operit.core.telemetry

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * KM-local runtime journal.
 *
 * Stores only stable runtime metadata. Message bodies, prompts, tool arguments/results,
 * credentials, form values, clipboard contents, and attachment contents are never accepted.
 */
object KmRuntimeJournal {
    private const val FILE_NAME = "km-runtime-events.jsonl"
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024L
    private const val RETAIN_AFTER_ROTATION = 800

    private val allowedFields = setOf(
        "toolId", "hookId", "stage", "route", "moduleId", "componentId",
        "componentType", "action", "durationMs", "success", "resultCode",
        "screenName", "source"
    )

    @Volatile
    private var journalFile: File? = null

    @Synchronized
    fun initialize(context: Context) {
        journalFile = File(context.applicationContext.filesDir, FILE_NAME)
    }

    @Synchronized
    fun record(event: String, fields: Map<String, Any?> = emptyMap()) {
        val file = journalFile ?: return
        try {
            val row = JSONObject()
                .put("timestampMs", System.currentTimeMillis())
                .put("elapsedRealtimeNs", SystemClock.elapsedRealtimeNanos())
                .put("event", event.take(48))
            fields.forEach { (key, value) ->
                if (key !in allowedFields || value == null) return@forEach
                when (value) {
                    is Boolean, is Int, is Long -> row.put(key, value)
                    is String -> row.put(key, value.take(96))
                }
            }
            val line = row.toString() + "\n"
            val bytes = line.toByteArray(StandardCharsets.UTF_8)
            if (file.exists() && file.length() + bytes.size > MAX_FILE_BYTES) {
                val retained = file.readLines(Charsets.UTF_8)
                    .takeLast(RETAIN_AFTER_ROTATION)
                    .joinToString(separator = "\n", postfix = "\n")
                file.writeText(retained, Charsets.UTF_8)
            }
            file.appendBytes(bytes)
        } catch (_: Exception) {
            // Telemetry must never interrupt agent execution.
        }
    }

    @Synchronized
    fun recentEventsSummary(limit: Int = 30): String {
        val file = journalFile ?: return "暂无 KM 运行事件。"
        if (!file.isFile) return "暂无 KM 运行事件。"
        return try {
            file.readLines(Charsets.UTF_8)
                .takeLast(limit.coerceIn(1, 60))
                .joinToString("\n")
                .takeLast(12_000)
                .ifBlank { "暂无 KM 运行事件。" }
        } catch (_: Exception) {
            "KM 运行事件暂不可读取。"
        }
    }

    @Synchronized
    fun clear() {
        runCatching { journalFile?.writeText("", Charsets.UTF_8) }
    }
}
