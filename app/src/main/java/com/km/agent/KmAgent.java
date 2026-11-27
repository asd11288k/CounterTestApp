package com.km.agent;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KmAgent {
    private static final String PREFS = "km_settings";
    private static final int MAX_TOOL_ROUNDS = 10;
    private static final int MAX_TOOL_CALLS = 20;

    public interface ToolCallbacks {
        boolean confirm(String toolName, String args);
        void onStatus(String message);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private int toolCount = 0;

    public KmAgent(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String run(List<JSONObject> history, ToolCallbacks callbacks) throws Exception {
        String endpoint = prefs.getString("endpoint", "https://api.openai.com/v1/chat/completions").trim();
        String model = prefs.getString("model", "gpt-4o-mini").trim();
        String apiKey = prefs.getString("api_key", "").trim();
        String customPrompt = prefs.getString("custom_prompt", "");
        boolean nativeTools = prefs.getBoolean("native_tools", true);

        if (endpoint.isEmpty()) throw new IllegalArgumentException("API 地址不能为空。");
        if (model.isEmpty()) throw new IllegalArgumentException("模型名称不能为空。");
        if (apiKey.isEmpty()) throw new IllegalArgumentException("API Key 不能为空，请在模型设置中填写。");

        String systemPrompt = KmPromptComposer.compose(context, nativeTools, customPrompt);
        JSONArray toolSchemas = KmTools.schemas();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            callbacks.onStatus(round == 0 ? "正在请求模型…" : "正在处理工具结果并继续推理…");
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            for (JSONObject item : history) messages.put(item);

            JSONObject request = new JSONObject();
            request.put("model", model);
            request.put("messages", messages);
            request.put("temperature", 0.35);
            request.put("max_tokens", 4096);
            if (nativeTools) {
                request.put("tools", toolSchemas);
                request.put("tool_choice", "auto");
            }

            JSONObject response = post(endpoint, apiKey, request);
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IllegalStateException("模型响应缺少 choices：" + response.toString());
            }
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) throw new IllegalStateException("模型响应缺少 message。");

            JSONArray calls = message.optJSONArray("tool_calls");
            if (calls != null && calls.length() > 0) {
                history.add(message);
                for (int i = 0; i < calls.length(); i++) {
                    if (++toolCount > MAX_TOOL_CALLS) {
                        throw new IllegalStateException("单次任务工具调用超过安全上限 " + MAX_TOOL_CALLS + "。");
                    }
                    JSONObject call = calls.getJSONObject(i);
                    String id = call.optString("id", "km_" + UUID.randomUUID().toString().replace("-", ""));
                    JSONObject fn = call.optJSONObject("function");
                    String name = fn == null ? "" : fn.optString("name", "");
                    String rawArgs = fn == null ? "{}" : fn.optString("arguments", "{}");
                    JSONObject args;
                    try {
                        args = new JSONObject(rawArgs);
                    } catch (Exception e) {
                        args = new JSONObject();
                    }
                    callbacks.onStatus("准备执行工具：" + name);
                    String output = executeTool(name, args, callbacks);
                    JSONObject toolMessage = new JSONObject();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_call_id", id);
                    toolMessage.put("name", name);
                    toolMessage.put("content", output);
                    history.add(toolMessage);
                }
                continue;
            }

            String text = message.optString("content", "");
            if (text == null) text = "";
            if (containsXmlTool(text)) {
                history.add(new JSONObject().put("role", "assistant").put("content", text));
                StringBuilder results = new StringBuilder();
                Matcher matcher = xmlToolPattern().matcher(text);
                while (matcher.find()) {
                    if (++toolCount > MAX_TOOL_CALLS) {
                        throw new IllegalStateException("单次任务工具调用超过安全上限 " + MAX_TOOL_CALLS + "。");
                    }
                    String name = matcher.group(1);
                    JSONObject args = parseXmlParams(matcher.group(2));
                    callbacks.onStatus("准备执行工具：" + name);
                    String output = executeTool(name, args, callbacks);
                    results.append("<tool_result name=\"").append(escapeXml(name))
                            .append("\">").append(escapeXml(output)).append("</tool_result>\n");
                }
                history.add(new JSONObject().put("role", "user")
                        .put("content", "以下是工具执行结果。请根据结果继续任务：\n" + results));
                continue;
            }

            history.add(new JSONObject().put("role", "assistant").put("content", text));
            callbacks.onStatus("任务完成");
            return text.trim().isEmpty() ? "模型返回了空内容。" : text.trim();
        }
        return "已达到本次任务的工具调用轮数上限。我停止了继续执行，请检查当前状态后再继续。";
    }

    private String executeTool(String name, JSONObject args, ToolCallbacks callbacks) {
        if (name == null || name.trim().isEmpty()) return error("工具名称为空");
        if (!isKnownTool(name)) return error("未知工具：" + name);

        if (KmTools.requiresApproval(name)) {
            boolean allowed = callbacks.confirm(name, args.toString());
            if (!allowed) return error("用户拒绝或取消了本次操作。不要重试同一动作，等待用户下一步指示。");
        }
        try {
            return KmTools.execute(context, name, args);
        } catch (Exception e) {
            return error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private boolean isKnownTool(String name) {
        JSONArray tools = KmTools.schemas();
        for (int i = 0; i < tools.length(); i++) {
            JSONObject fn = tools.optJSONObject(i);
            JSONObject spec = fn == null ? null : fn.optJSONObject("function");
            if (spec != null && name.equals(spec.optString("name"))) return true;
        }
        return false;
    }

    private JSONObject post(String endpoint, String apiKey, JSONObject body) throws Exception {
        String urlText = normalizeEndpoint(endpoint);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(25000);
            connection.setReadTimeout(120000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = readAll(stream);
            if (status < 200 || status >= 300) {
                if (responseText.length() > 2200) responseText = responseText.substring(0, 2200);
                throw new IllegalStateException("API 请求失败，HTTP " + status + "：\n" + responseText);
            }
            return new JSONObject(responseText);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String normalizeEndpoint(String endpoint) {
        String value = endpoint.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith("/chat/completions")) return value;
        if (value.endsWith("/v1")) return value + "/chat/completions";
        return value + "/v1/chat/completions";
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        return out.toString();
    }

    private static Pattern xmlToolPattern() {
        return Pattern.compile("<tool\\s+name\\s*=\\s*[\"']([^\"']+)[\"']\\s*>(.*?)</tool>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    private static Pattern xmlParamPattern() {
        return Pattern.compile("<param\\s+name\\s*=\\s*[\"']([^\"']+)[\"']\\s*>(.*?)</param>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    private static boolean containsXmlTool(String text) {
        return text != null && xmlToolPattern().matcher(text).find();
    }

    private static JSONObject parseXmlParams(String body) {
        JSONObject args = new JSONObject();
        Matcher matcher = xmlParamPattern().matcher(body);
        while (matcher.find()) {
            try {
                args.put(matcher.group(1), unescapeXml(matcher.group(2).trim()));
            } catch (Exception ignored) { }
        }
        return args;
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String unescapeXml(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static String error(String message) {
        try {
            return new JSONObject().put("success", false).put("error", message).toString();
        } catch (Exception e) {
            return "{\"success\":false}";
        }
    }
}
