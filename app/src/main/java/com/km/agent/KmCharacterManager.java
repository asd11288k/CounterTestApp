package com.km.agent;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/** Persistent, editable character-card profiles used by the dynamic prompt composer. */
public final class KmCharacterManager {
    private static final String PREFS = "km_settings";
    private static final String CARDS_KEY = "km_character_cards_v1";
    private static final String ACTIVE_KEY = "km_active_character_v1";

    private KmCharacterManager() {}

    public static synchronized JSONArray list(Context context) {
        ensureDefaults(context);
        try {
            return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(CARDS_KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static synchronized void ensureDefaults(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(CARDS_KEY, "");
        JSONArray cards;
        try {
            cards = raw == null || raw.trim().isEmpty() ? new JSONArray() : new JSONArray(raw);
        } catch (Exception e) {
            cards = new JSONArray();
        }

        addDefault(cards, "km-assistant", "KM 助理", "全能、严谨、主动执行", "✦",
                "你是 km 的默认个人助理。先理解目标和限制，再用清晰、自然的中文完成任务。复杂任务先拆解，执行前核实状态，执行后验证结果。不要编造已完成的事项。",
                "你好，我是 km。把目标告诉我，我会先理解，再稳妥地帮你完成。");
        addDefault(cards, "technical-advisor", "技术顾问", "软件、设备、开发与排障", "⌘",
                "你是一名资深技术顾问。优先给出准确结论、可复现依据和最短操作路径。区分已验证事实、推断与未知；代码必须可运行，排障按证据推进，避免重复已经失败的步骤。",
                "把设备、软件或报错发给我，我会按证据定位问题。");
        addDefault(cards, "writing-partner", "写作搭档", "创作、润色与结构化表达", "✎",
                "你是一名专业写作搭档。根据任务选择合适文体，重视结构、节奏、具体细节与语言质感。除非用户要求解释，否则优先交付完整成稿，不用空泛套话填充内容。",
                "告诉我主题、用途或想要的风格，我们直接开始创作。");
        addDefault(cards, "roleplay-companion", "角色伙伴", "沉浸式对话与角色扮演", "☾",
                "你是一名沉浸式角色扮演搭档。保持角色口吻、情绪连续性和场景一致性，以自然对话推进互动；不要在角色扮演中频繁跳出解释规则，除非用户明确要求。",
                "我在这里。你想从哪个场景开始？");

        prefs.edit().putString(CARDS_KEY, cards.toString()).apply();
        String active = prefs.getString(ACTIVE_KEY, "");
        if (find(cards, active) == null) {
            prefs.edit().putString(ACTIVE_KEY, "km-assistant").apply();
        }
    }

    private static void addDefault(JSONArray cards, String id, String name, String subtitle,
                                   String icon, String prompt, String greeting) {
        if (find(cards, id) != null) return;
        try {
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("name", name);
            item.put("subtitle", subtitle);
            item.put("icon", icon);
            item.put("systemPrompt", prompt);
            item.put("greeting", greeting);
            item.put("builtin", true);
            cards.put(item);
        } catch (Exception ignored) { }
    }

    public static JSONObject find(JSONArray cards, String id) {
        if (cards == null || id == null) return null;
        for (int i = 0; i < cards.length(); i++) {
            JSONObject item = cards.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) return item;
        }
        return null;
    }

    public static String getActiveId(Context context) {
        ensureDefaults(context);
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(ACTIVE_KEY, "km-assistant");
    }

    public static void setActiveId(Context context, String id) {
        JSONArray cards = list(context);
        if (find(cards, id) != null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(ACTIVE_KEY, id).apply();
        }
    }

    public static JSONObject getActive(Context context) {
        JSONArray cards = list(context);
        JSONObject item = find(cards, getActiveId(context));
        return item == null ? find(cards, "km-assistant") : item;
    }

    public static String getActivePrompt(Context context) {
        JSONObject item = getActive(context);
        if (item == null) return "你是 km，一个可靠的个人 AI 助理。";
        return "当前角色卡：" + item.optString("name", "KM 助理") + "\n"
                + "角色定位：" + item.optString("subtitle", "") + "\n"
                + "角色指令：\n" + item.optString("systemPrompt", "")
                + "\n角色开场语（仅在合适时使用，不要机械重复）："
                + item.optString("greeting", "");
    }

    public static synchronized String save(Context context, String id, String name, String subtitle,
                                            String icon, String prompt, String greeting) throws Exception {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("角色名称不能为空。");
        if (prompt == null || prompt.trim().isEmpty()) throw new IllegalArgumentException("角色设定不能为空。");
        if (prompt.length() > 12000) throw new IllegalArgumentException("角色设定最多 12000 个字符。");
        JSONArray cards = list(context);
        String cardId = id == null || id.trim().isEmpty() ? "custom-" + UUID.randomUUID() : id;
        JSONObject item = find(cards, cardId);
        if (item == null) {
            item = new JSONObject();
            item.put("id", cardId);
            item.put("builtin", false);
            cards.put(item);
        }
        item.put("name", name.trim());
        item.put("subtitle", subtitle == null ? "" : subtitle.trim());
        item.put("icon", icon == null || icon.trim().isEmpty() ? "✦" : icon.trim());
        item.put("systemPrompt", prompt.trim());
        item.put("greeting", greeting == null ? "" : greeting.trim());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(CARDS_KEY, cards.toString()).apply();
        return cardId;
    }

    public static synchronized boolean delete(Context context, String id) {
        JSONArray cards = list(context);
        JSONArray next = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < cards.length(); i++) {
            JSONObject item = cards.optJSONObject(i);
            if (item == null) continue;
            if (id.equals(item.optString("id")) && !item.optBoolean("builtin", false)) {
                removed = true;
                continue;
            }
            next.put(item);
        }
        if (removed) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(CARDS_KEY, next.toString()).apply();
            if (id.equals(prefs.getString(ACTIVE_KEY, ""))) {
                prefs.edit().putString(ACTIVE_KEY, "km-assistant").apply();
            }
        }
        return removed;
    }
}
