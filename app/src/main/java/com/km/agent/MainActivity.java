package com.km.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final String PREFS = "km_settings";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<JSONObject> history = new ArrayList<>();

    private LinearLayout chatColumn;
    private ScrollView chatScroll;
    private EditText input;
    private TextView status;
    private Button accessButton;
    private Button sendButton;
    private boolean busy = false;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadHistory();
        buildUi();
        if (history.isEmpty()) {
            addBubble("km", "你好，我是 km。配置模型 API 后，我可以聊天，也可以在你授权后查看和操作手机界面。", false);
        } else {
            for (JSONObject item : history) {
                String role = item.optString("role", "");
                if ("user".equals(role)) addBubble("你", item.optString("content", ""), true);
                else if ("assistant".equals(role) && item.optString("content", "").length() > 0) {
                    addBubble("km", item.optString("content", ""), false);
                }
            }
        }
        updateAccessButton();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(8));
        root.setBackgroundColor(Color.rgb(246, 247, 249));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("km");
        title.setTextSize(27);
        title.setTextColor(Color.rgb(24, 35, 52));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button settingsButton = new Button(this);
        settingsButton.setText("模型设置");
        settingsButton.setOnClickListener(v -> showSettings());
        top.addView(settingsButton);

        accessButton = new Button(this);
        accessButton.setText("手机权限");
        accessButton.setOnClickListener(v -> openAccessibilitySettings());
        top.addView(accessButton);
        root.addView(top);

        TextView subtitle = new TextView(this);
        subtitle.setText("个人 Android Agent · 模型由你配置");
        subtitle.setTextColor(Color.GRAY);
        subtitle.setTextSize(12);
        subtitle.setPadding(0, 0, 0, dp(8));
        root.addView(subtitle);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatColumn = new LinearLayout(this);
        chatColumn.setOrientation(LinearLayout.VERTICAL);
        chatColumn.setPadding(dp(2), dp(6), dp(2), dp(12));
        chatScroll.addView(chatColumn);
        root.addView(chatScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        status = new TextView(this);
        status.setText("就绪");
        status.setTextColor(Color.GRAY);
        status.setTextSize(12);
        status.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.addView(status);

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);
        composer.setOrientation(LinearLayout.HORIZONTAL);

        input = new EditText(this);
        input.setHint("给 km 下达任务…");
        input.setTextSize(16);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        sendButton = new Button(this);
        sendButton.setText("发送");
        sendButton.setOnClickListener(v -> sendMessage());
        composer.addView(sendButton);
        root.addView(composer);

        setContentView(root);
    }

    private void addBubble(String who, String text, boolean user) {
        TextView bubble = new TextView(this);
        bubble.setText(who + "\n" + text);
        bubble.setTextSize(15);
        bubble.setTextColor(user ? Color.WHITE : Color.rgb(30, 39, 52));
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(user ? Color.rgb(48, 104, 190) : Color.WHITE);
        bubble.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(user ? 34 : 2), dp(5), dp(user ? 2 : 34), dp(5));
        chatColumn.addView(bubble, lp);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void updateStatus(String text) {
        mainHandler.post(() -> status.setText(text));
    }

    private void sendMessage() {
        if (busy) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;

        String key = getSharedPreferences(PREFS, MODE_PRIVATE).getString("api_key", "");
        if (key.trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("先配置模型")
                    .setMessage("需要先填写 API 地址、模型名称和 API Key。")
                    .setPositiveButton("去设置", (d, w) -> showSettings())
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        input.setText("");
        try {
            history.add(new JSONObject().put("role", "user").put("content", text));
        } catch (Exception ignored) { }
        addBubble("你", text, true);
        persistHistory();
        busy = true;
        sendButton.setEnabled(false);
        updateStatus("km 正在思考…");

        executor.execute(() -> {
            try {
                KmAgent agent = new KmAgent(this);
                String answer = agent.run(history, new KmAgent.ToolCallbacks() {
                    @Override
                    public boolean confirm(String toolName, String args) {
                        return askForToolApproval(toolName, args);
                    }

                    @Override
                    public void onStatus(String message) {
                        updateStatus(message);
                    }
                });
                mainHandler.post(() -> {
                    addBubble("km", answer, false);
                    updateStatus("完成");
                    persistHistory();
                    busy = false;
                    sendButton.setEnabled(true);
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                mainHandler.post(() -> {
                    addBubble("km · 错误", message, false);
                    updateStatus("请求失败");
                    busy = false;
                    sendButton.setEnabled(true);
                    persistHistory();
                });
            }
        });
    }

    private boolean askForToolApproval(String toolName, String args) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean approved = new AtomicBoolean(false);
        mainHandler.post(() -> {
            if (isFinishing() || isDestroyed()) {
                latch.countDown();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("km 请求执行手机操作")
                    .setMessage("工具：" + toolName + "\n参数：" + args
                            + "\n\n只有你点击“允许执行”后，km 才会操作手机。")
                    .setPositiveButton("允许执行", (d, w) -> {
                        approved.set(true);
                        latch.countDown();
                    })
                    .setNegativeButton("拒绝", (d, w) -> latch.countDown())
                    .setOnCancelListener(d -> latch.countDown())
                    .show();
        });
        try {
            return latch.await(150, TimeUnit.SECONDS) && approved.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void showSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(18), dp(8), dp(18), dp(4));

        EditText endpoint = field("API 地址", prefs.getString("endpoint", "https://api.openai.com/v1/chat/completions"));
        EditText model = field("模型名称", prefs.getString("model", "gpt-4o-mini"));
        EditText apiKey = field("API Key", prefs.getString("api_key", ""));
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText customPrompt = field("附加系统提示词（可留空）", prefs.getString("custom_prompt", ""));
        customPrompt.setMinLines(2);
        customPrompt.setMaxLines(4);

        fields.addView(endpoint);
        fields.addView(model);
        fields.addView(apiKey);
        fields.addView(customPrompt);

        Switch nativeTools = new Switch(this);
        nativeTools.setText("使用原生函数调用（不兼容时可关闭，改用 XML 工具协议）");
        nativeTools.setChecked(prefs.getBoolean("native_tools", true));
        fields.addView(nativeTools);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        new AlertDialog.Builder(this)
                .setTitle("km 模型设置")
                .setView(scroll)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.edit()
                            .putString("endpoint", endpoint.getText().toString().trim())
                            .putString("model", model.getText().toString().trim())
                            .putString("api_key", apiKey.getText().toString().trim())
                            .putString("custom_prompt", customPrompt.getText().toString())
                            .putBoolean("native_tools", nativeTools.isChecked())
                            .apply();
                    updateStatus("模型设置已保存");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText field(String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(false);
        edit.setTextSize(14);
        return edit;
    }

    private void openAccessibilitySettings() {
        new AlertDialog.Builder(this)
                .setTitle("启用 km 手机操作")
                .setMessage("需要你手动在 Android 无障碍设置中启用 km。启用后，km 才能读取当前界面并按次请求执行点击、滑动和输入。所有会改变手机界面的操作默认逐次询问你。")
                .setPositiveButton("打开无障碍设置", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception e) {
                        updateStatus("无法打开无障碍设置");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateAccessButton() {
        if (accessButton != null) {
            accessButton.setText(KmAccessibilityService.isEnabled(this) ? "手机操作已启用" : "启用手机操作");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessButton();
    }

    private void loadHistory() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString("history", "[]");
        try {
            JSONArray array = new JSONArray(saved);
            int start = Math.max(0, array.length() - 60);
            for (int i = start; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) history.add(item);
            }
        } catch (Exception ignored) { }
    }

    private void persistHistory() {
        JSONArray array = new JSONArray();
        int start = Math.max(0, history.size() - 60);
        for (int i = start; i < history.size(); i++) array.put(history.get(i));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("history", array.toString()).apply();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
