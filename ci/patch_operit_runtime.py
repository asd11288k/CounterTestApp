#!/usr/bin/env python3
"""Apply KM's native telemetry and prompt-context integration to pinned Operit."""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
operit = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root / "vendor" / "operit"
source = root / "patches" / "operit" / "KmRuntimeJournal.kt"
target = operit / "app/src/main/java/com/ai/assistance/operit/core/telemetry/KmRuntimeJournal.kt"
if not source.is_file():
    raise SystemExit(f"KM runtime journal source missing: {source}")
target.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(source, target)

def patch(path: Path, replacements: list[tuple[str, str, int]]) -> None:
    text = path.read_text(encoding="utf-8")
    for old, new, expected in replacements:
        count = text.count(old)
        if count != expected:
            raise SystemExit(f"Patch precondition failed for {path}: expected {expected} matches, got {count}: {old[:100]!r}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")

hooks = operit / "app/src/main/java/com/ai/assistance/operit/core/chat/hooks/PromptHookRegistry.kt"
patch(hooks, [
    (
        "import com.ai.assistance.operit.util.AppLogger\n",
        "import com.ai.assistance.operit.util.AppLogger\nimport com.ai.assistance.operit.core.telemetry.KmRuntimeJournal\nimport android.os.SystemClock\n",
        1,
    ),
    (
        """        hooks.forEach { hook ->
            val mutation =
                runCatching { invoke(hook, current) }
                    .onFailure { error ->
                        AppLogger.e(TAG, "$hookLabel callback failed", error)
                    }
                    .getOrNull()
                    ?: return@forEach
            current = applyMutation(current, mutation)
        }""",
        """        hooks.forEach { hook ->
            val hookStartedAt = SystemClock.elapsedRealtime()
            val mutation =
                runCatching { invoke(hook, current) }
                    .onFailure { error ->
                        AppLogger.e(TAG, "$hookLabel callback failed", error)
                    }
                    .getOrNull()
            KmRuntimeJournal.record(
                event = "prompt.hook",
                fields = mapOf(
                    "stage" to hookLabel,
                    "durationMs" to (SystemClock.elapsedRealtime() - hookStartedAt),
                    "success" to (mutation != null),
                    "source" to "native"
                )
            )
            if (mutation != null) current = applyMutation(current, mutation)
        }""",
        1,
    ),
])

tools = operit / "app/src/main/java/com/ai/assistance/operit/api/chat/enhance/ToolExecutionManager.kt"
patch(tools, [
    (
        "package com.ai.assistance.operit.api.chat.enhance\n",
        "package com.ai.assistance.operit.api.chat.enhance\n\nimport com.ai.assistance.operit.core.telemetry.KmRuntimeJournal\nimport android.os.SystemClock\n",
        1,
    ),
    (
        """            async {
                val result =
                    executeTool(
                        invocation = invocation,
                        toolHandler = toolHandler,
                        packageManager = packageManager,
                        runtimeContext = toolRuntimeContext
                    )
                orderedResults.complete(index, result)
            }""",
        """            async {
                val startedAt = SystemClock.elapsedRealtime()
                KmRuntimeJournal.record("tool.invoke", mapOf(
                    "toolId" to invocation.tool.name,
                    "source" to "native"
                ))
                val result =
                    executeTool(
                        invocation = invocation,
                        toolHandler = toolHandler,
                        packageManager = packageManager,
                        runtimeContext = toolRuntimeContext
                    )
                KmRuntimeJournal.record("tool.result", mapOf(
                    "toolId" to invocation.tool.name,
                    "durationMs" to (SystemClock.elapsedRealtime() - startedAt),
                    "success" to result.success,
                    "source" to "native"
                ))
                orderedResults.complete(index, result)
            }""",
        1,
    ),
    (
        """        for ((index, invocation) in serialInvocations) {
            val result =
                executeTool(
                    invocation = invocation,
                    toolHandler = toolHandler,
                    packageManager = packageManager,
                    runtimeContext = toolRuntimeContext
                )
            orderedResults.complete(index, result)
        }""",
        """        for ((index, invocation) in serialInvocations) {
            val startedAt = SystemClock.elapsedRealtime()
            KmRuntimeJournal.record("tool.invoke", mapOf(
                "toolId" to invocation.tool.name,
                "source" to "native"
            ))
            val result =
                executeTool(
                    invocation = invocation,
                    toolHandler = toolHandler,
                    packageManager = packageManager,
                    runtimeContext = toolRuntimeContext
                )
            KmRuntimeJournal.record("tool.result", mapOf(
                "toolId" to invocation.tool.name,
                "durationMs" to (SystemClock.elapsedRealtime() - startedAt),
                "success" to result.success,
                "source" to "native"
            ))
            orderedResults.complete(index, result)
        }""",
        1,
    ),
])

application = operit / "app/src/main/java/com/ai/assistance/operit/core/application/OperitApplication.kt"
patch(application, [
    (
        "import com.ai.assistance.operit.util.AppLogger\n",
        "import com.ai.assistance.operit.util.AppLogger\nimport com.ai.assistance.operit.core.telemetry.KmRuntimeJournal\nimport com.ai.assistance.operit.core.chat.hooks.PromptHookRegistry\nimport com.ai.assistance.operit.core.chat.hooks.PromptHookContext\nimport com.ai.assistance.operit.core.chat.hooks.PromptHookMutation\nimport com.ai.assistance.operit.core.chat.hooks.SystemPromptComposeHook\n",
        1,
    ),
    (
        """    override fun onCreate() {
        super.onCreate()
        val startTime = System.currentTimeMillis()""",
        """    override fun onCreate() {
        super.onCreate()
        KmRuntimeJournal.initialize(this)
        PromptHookRegistry.registerSystemPromptComposeHook(object : SystemPromptComposeHook {
            override val id: String = "km.runtime.context"

            override fun onEvent(context: PromptHookContext): PromptHookMutation {
                val runtimeContext = buildString {
                    appendLine("[KM Runtime Context]")
                    appendLine("The active Android agent runtime is Operit's native Kotlin implementation.")
                    appendLine("Prompt assembly uses SystemPromptConfig and PromptHookRegistry.")
                    appendLine("Tool execution uses ToolExecutionManager; extension systems include Skills, ToolPkg and MCP.")
                    appendLine("The following local event records contain metadata only, not message or prompt contents:")
                    append(KmRuntimeJournal.recentEventsSummary())
                }
                val existing = context.systemPrompt.orEmpty()
                return PromptHookMutation(
                    systemPrompt = if (existing.isBlank()) runtimeContext else "$existing\\n\\n$runtimeContext"
                )
            }
        })
        val startTime = System.currentTimeMillis()""",
        1,
    ),
])

print("Applied KM native runtime journal, prompt hook, and tool execution instrumentation.")
