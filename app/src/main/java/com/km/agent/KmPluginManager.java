package com.km.agent;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Safe declarative plugin registry.
 * Packages may contribute prompt sections and may expose only tools already
 * implemented and permission-guarded by KM. No arbitrary APK/DEX/script is loaded.
 */
public final class KmPluginManager {
    private static final String PREFS = "km_settings";
    private static final String PLUGINS_KEY = "km_plugins_v1";
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{2,47}");

    private KmPluginManager() {}

    public static synchronized JSONArray list(Context context) {
        ensureDefaults(context);
        try {
            return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(PLUGINS_KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static synchronized void ensureDefaults(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray plugins;
        try {
            String raw = prefs.getString(PLUGINS_KEY, "");
            plugins = raw == null || raw.trim().isEmpty() ? new JSONArray() : new JSONArray(raw);
        } catch (Exception e) {
            plugins = new JSONArray();
        }

        addBuiltin(plugins, "phone_automation", "手机自动化", "读取界面、点击、输入、滑动与应用启动",
                "为完成用户明确要求的手机任务，先读取界面控件树，依据实时 bounds 操作。任何会改变界面或设备状态的工具都必须逐次获得用户确认。不要绕过系统权限、验证或确认页面。",
                new String[]{"get_screen","tap","long_press","swipe","click_text","set_text","press_back","press_home","open_app"});
        addBuiltin(plugins, "private_workspace", "私有工作区", "在 km 应用私有目录中管理文本资料",
                "用户工作区位于 km 私有目录。只有在用户任务需要时才读取文件；写入或删除前必须准确说明目标路径和内容，并等待确认。不得尝试路径穿越或声称访问工作区以外的文件。",
                new String[]{"list_workspace","read_file","write_file","delete_file"});
        addBuiltin(plugins, "planning", "任务规划", "复杂任务拆解、状态跟踪与结果校验",
                "复杂任务使用计划驱动执行：先列出目标与约束，拆成可验证步骤；每步执行后检查工具结果，根据结果调整后续步骤。避免重复失败调用，结束时给出已完成、未完成与风险。",
                new String[]{});
        addBuiltin(plugins, "writing", "写作与资料整理", "写作、摘要、改写和结构化文档",
                "写作时先识别目标读者、用途和文体。优先交付完整、可直接使用的成品；保持术语一致、结构清晰，并区分来源事实与推测。",
                new String[]{});

        prefs.edit().putString(PLUGINS_KEY, plugins.toString()).apply();
    }

    private static void addBuiltin(JSONArray plugins, String id, String name, String description,
                                   String prompt, String[] tools) {
        if (find(plugins, id) != null) return;
        try {
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("name", name);
            item.put("version", "1.0.0");
            item.put("description", description);
            item.put("systemPrompt", prompt);
            JSONArray toolArray = new JSONArray();
            for (String tool : tools) toolArray.put(tool);
            item.put("tools", toolArray);
            item.put("enabled", true);
            item.put("builtin", true);
            plugins.put(item);
        } catch (Exception ignored) { }
    }

    public static JSONObject find(JSONArray plugins, String id) {
        if (plugins == null || id == null) return null;
        for (int i = 0; i < plugins.length(); i++) {
            JSONObject item = plugins.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) return item;
        }
        return null;
    }

    public static Set<String> enabledToolNames(Context context) {
        JSONArray plugins = list(context);
        Set<String> tools = new HashSet<>();
        for (int i = 0; i < plugins.length(); i++) {
            JSONObject item = plugins.optJSONObject(i);
            if (item == null || !item.optBoolean("enabled", false)) continue;
            JSONArray declared = item.optJSONArray("tools");
            if (declared == null) continue;
            for (int j = 0; j < declared.length(); j++) {
                String name = declared.optString(j, "");
                if (isSupportedTool(name)) tools.add(name);
            }
        }
        return tools;
    }

    public static String enabledPromptSections(Context context) {
        JSONArray plugins = list(context);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < plugins.length(); i++) {
            JSONObject item = plugins.optJSONObject(i);
            if (item == null || !item.optBoolean("enabled", false)) continue;
            String fragment = item.optString("systemPrompt", "").trim();
            if (fragment.isEmpty()) continue;
            if (out.length() > 0) out.append("\n\n");
            out.append("【").append(item.optString("name", item.optString("id"))).append("】\n")
                    .append(fragment);
        }
        return out.toString();
    }

    public static void setEnabled(Context context, String id, boolean enabled) {
        JSONArray plugins = list(context);
        JSONObject item = find(plugins, id);
        if (item == null) return;
        try {
            item.put("enabled", enabled);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PLUGINS_KEY, plugins.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static synchronized String installPackage(Context context, JSONObject manifest) throws Exception {
        if (manifest == null) throw new IllegalArgumentException("插件文件为空。");
        if (manifest.optInt("apiVersion", 0) != 1) {
            throw new IllegalArgumentException("不支持的插件格式：apiVersion 必须为 1。");
        }
        String id = manifest.optString("id", "").trim();
        String name = manifest.optString("name", "").trim();
        if (!ID_PATTERN.matcher(id).matches()) throw new IllegalArgumentException("插件 id 格式不正确。");
        if (name.isEmpty()) throw new IllegalArgumentException("插件名称不能为空。");
        if (name.length() > 60) throw new IllegalArgumentException("插件名称过长。");
        String prompt = manifest.optString("systemPrompt", "");
        if (prompt.length() > 12000) throw new IllegalArgumentException("插件提示词最多 12000 个字符。");

        JSONArray rawTools = manifest.optJSONArray("tools");
        JSONArray safeTools = new JSONArray();
        if (rawTools != null) {
            for (int i = 0; i < rawTools.length(); i++) {
                String tool = rawTools.optString(i, "");
                if (!isSupportedTool(tool)) throw new IllegalArgumentException("插件试图注册 KM 未开放的工具：" + tool);
                if (!contains(safeTools, tool)) safeTools.put(tool);
            }
        }

        JSONArray plugins = list(context);
        JSONObject item = find(plugins, id);
        if (item == null) {
            item = new JSONObject();
            item.put("id", id);
            item.put("builtin", false);
            plugins.put(item);
        } else if (item.optBoolean("builtin", false)) {
            throw new IllegalArgumentException("不能覆盖 KM 内置插件。");
        }
        item.put("name", name);
        item.put("version", manifest.optString("version", "1.0.0"));
        item.put("description", manifest.optString("description", ""));
        item.put("systemPrompt", prompt);
        item.put("tools", safeTools);
        item.put("enabled", false);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PLUGINS_KEY, plugins.toString()).apply();
        return name;
    }

    public static synchronized boolean uninstall(Context context, String id) {
        JSONArray plugins = list(context);
        JSONArray next = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < plugins.length(); i++) {
            JSONObject item = plugins.optJSONObject(i);
            if (item == null) continue;
            if (id.equals(item.optString("id")) && !item.optBoolean("builtin", false)) {
                removed = true;
                continue;
            }
            next.put(item);
        }
        if (removed) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PLUGINS_KEY, next.toString()).apply();
        }
        return removed;
    }

    private static boolean contains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) if (value.equals(array.optString(i))) return true;
        return false;
    }

    private static boolean isSupportedTool(String name) {
        switch (name) {
            case "get_screen": case "tap": case "long_press": case "swipe":
            case "click_text": case "set_text": case "press_back": case "press_home":
            case "open_app": case "list_workspace": case "read_file": case "write_file":
            case "delete_file": return true;
            default: return false;
        }
    }
}
