package com.km.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final String PREFS = "km_settings";
    private static final String STORE_SESSIONS = "chat_sessions_v2";
    private static final String STORE_ACTIVE = "active_session_v2";

    private static final int BG = Color.rgb(10, 15, 27);
    private static final int PANEL = Color.rgb(19, 27, 44);
    private static final int PANEL_2 = Color.rgb(25, 35, 55);
    private static final int BORDER = Color.rgb(38, 52, 76);
    private static final int TEXT = Color.rgb(235, 241, 250);
    private static final int MUTED = Color.rgb(145, 160, 184);
    private static final int BLUE = Color.rgb(91, 145, 255);
    private static final int GREEN = Color.rgb(74, 202, 154);
    private static final int RED = Color.rgb(244, 111, 126);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<ChatSession> sessions = new ArrayList<>();

    private ChatSession currentSession;
    private LinearLayout root;
    private LinearLayout chatColumn;
    private ScrollView chatScroll;
    private EditText input;
    private TextView statusText;
    private TextView modelChip;
    private TextView accessChip;
    private TextView sendButton;
    private TextView stopButton;
    private boolean busy = false;
    private volatile KmAgent currentAgent;

    private static class ChatSession {
        String id;
        String title;
        long updatedAt;
        ArrayList<JSONObject> messages = new ArrayList<>();

        ChatSession(String id, String title) {
            this.id = id;
            this.title = title;
            this.updatedAt = System.currentTimeMillis();
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        loadSessions();
        buildUi();
        renderCurrentSession();
        updateBadges();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(8), dp(16), dp(10));

        root.addView(buildHeader());
        root.addView(buildStatusCards());
        root.addView(buildChatArea(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(buildComposer());

        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(5), 0, dp(14));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setOrientation(LinearLayout.HORIZONTAL);

        TextView mark = new TextView(this);
        mark.setText("k");
        mark.setTextColor(Color.WHITE);
        mark.setTextSize(25);
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(BLUE, 16, 0));
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(43), dp(43));
        markLp.rightMargin = dp(11);
        brand.addView(mark, markLp);

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        TextView name = label("km", 24, TEXT, true);
        TextView subtitle = label("PERSONAL AI AGENT", 9, MUTED, true);
        brandText.addView(name);
        brandText.addView(subtitle);
        brand.addView(brandText);
        header.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView historyButton = smallIconButton("☷", "会话列表");
        historyButton.setOnClickListener(v -> showSessions());
        header.addView(historyButton);

        TextView newButton = smallIconButton("＋", "新建会话");
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        newLp.leftMargin = dp(6);
        newButton.setLayoutParams(newLp);
        newButton.setOnClickListener(v -> createNewSession());
        header.addView(newButton);

        TextView moreButton = smallIconButton("⋯", "更多功能");
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        moreLp.leftMargin = dp(6);
        moreButton.setLayoutParams(moreLp);
        moreButton.setOnClickListener(v -> showMoreMenu());
        header.addView(moreButton);
        return header;
    }

    private View buildStatusCards() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout modelCard = new LinearLayout(this);
        modelCard.setOrientation(LinearLayout.VERTICAL);
        modelCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        modelCard.setBackground(rounded(PANEL, 15, BORDER));
        TextView modelCaption = label("MODEL", 9, MUTED, true);
        modelCard.addView(modelCaption);
        modelChip = label("尚未配置", 12, TEXT, true);
        LinearLayout.LayoutParams modelTextLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modelTextLp.topMargin = dp(4);
        modelCard.addView(modelChip, modelTextLp);
        modelCard.setOnClickListener(v -> showSettings());
        row.addView(modelCard, new LinearLayout.LayoutParams(0, dp(62), 1));

        LinearLayout accessCard = new LinearLayout(this);
        accessCard.setOrientation(LinearLayout.VERTICAL);
        accessCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        accessCard.setBackground(rounded(PANEL, 15, BORDER));
        LinearLayout.LayoutParams accessLp = new LinearLayout.LayoutParams(0, dp(62), 1);
        accessLp.leftMargin = dp(9);
        TextView accessCaption = label("DEVICE ACCESS", 9, MUTED, true);
        accessCard.addView(accessCaption);
        accessChip = label("无障碍未启用", 12, TEXT, true);
        LinearLayout.LayoutParams accessTextLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        accessTextLp.topMargin = dp(4);
        accessCard.addView(accessChip, accessTextLp);
        accessCard.setOnClickListener(v -> openAccessibilitySettings());
        row.addView(accessCard, accessLp);
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outer.bottomMargin = dp(12);
        row.setLayoutParams(outer);
        return row;
    }

    private View buildChatArea() {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setBackground(rounded(PANEL, 21, BORDER));
        frame.setClipToPadding(false);
        frame.setPadding(dp(12), dp(10), dp(12), dp(8));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView chatTitle = label("对话", 14, TEXT, true);
        heading.addView(chatTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView sessionName = label("当前会话", 10, MUTED, false);
        heading.addView(sessionName);
        frame.addView(heading);

        View line = new View(this);
        line.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lineLp.topMargin = dp(9);
        frame.addView(line, lineLp);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setVerticalScrollBarEnabled(false);
        chatColumn = new LinearLayout(this);
        chatColumn.setOrientation(LinearLayout.VERTICAL);
        chatColumn.setPadding(dp(2), dp(9), dp(2), dp(12));
        chatScroll.addView(chatColumn);
        frame.addView(chatScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return frame;
    }

    private View buildComposer() {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams areaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        areaLp.topMargin = dp(11);
        area.setLayoutParams(areaLp);

        LinearLayout shortcuts = new LinearLayout(this);
        shortcuts.setOrientation(LinearLayout.HORIZONTAL);
        shortcuts.setGravity(Gravity.CENTER_VERTICAL);
        addQuickAction(shortcuts, "◉  看屏幕", "读取当前手机界面，告诉我有哪些内容和可操作控件。");
        addQuickAction(shortcuts, "▣  打开应用", "帮我打开一个应用。先告诉我你需要应用名称。");
        addQuickAction(shortcuts, "⌘  规划任务", "先分析我的目标，给出一个简短执行计划；得到我确认后再操作手机。");
        area.addView(shortcuts);

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setPadding(dp(10), dp(6), dp(6), dp(6));
        composer.setBackground(rounded(PANEL_2, 19, BORDER));
        LinearLayout.LayoutParams composerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        composerLp.topMargin = dp(10);
        composer.setLayoutParams(composerLp);

        input = new EditText(this);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setHint("给 km 描述一个任务…");
        input.setTextSize(15);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setPadding(dp(7), dp(9), dp(6), dp(9));
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        stopButton = new TextView(this);
        stopButton.setText("■");
        stopButton.setTextSize(16);
        stopButton.setTextColor(Color.WHITE);
        stopButton.setGravity(Gravity.CENTER);
        stopButton.setBackground(rounded(RED, 15, 0));
        stopButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        stopLp.leftMargin = dp(5);
        composer.addView(stopButton, stopLp);
        stopButton.setOnClickListener(v -> cancelCurrentTask());

        sendButton = new TextView(this);
        sendButton.setText("↑");
        sendButton.setTextSize(23);
        sendButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sendButton.setTextColor(Color.WHITE);
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setBackground(rounded(BLUE, 15, 0));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        sendLp.leftMargin = dp(5);
        composer.addView(sendButton, sendLp);
        sendButton.setOnClickListener(v -> {
            if (busy) cancelCurrentTask(); else sendMessage();
        });
        area.addView(composer);

        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(dp(5), dp(7), dp(5), 0);
        statusText = label("km 已就绪", 10, MUTED, false);
        foot.addView(statusText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView privacy = label("工具操作需确认", 10, MUTED, false);
        foot.addView(privacy);
        area.addView(foot);
        return area;
    }

    private void addQuickAction(LinearLayout row, String caption, String prompt) {
        TextView chip = label(caption, 10, MUTED, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(7), dp(8), dp(7));
        chip.setBackground(rounded(PANEL_2, 12, BORDER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(32), 1);
        lp.rightMargin = dp(5);
        chip.setOnClickListener(v -> {
            input.setText(prompt);
            input.setSelection(input.length());
            input.requestFocus();
        });
        row.addView(chip, lp);
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView smallIconButton(String text, String description) {
        TextView button = label(text, 21, TEXT, false);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(rounded(PANEL_2, 14, BORDER));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return button;
    }

    private GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void renderCurrentSession() {
        chatColumn.removeAllViews();
        if (currentSession == null || currentSession.messages.isEmpty()) {
            addWelcome();
            return;
        }
        for (JSONObject item : currentSession.messages) {
            String role = item.optString("role", "");
            String content = item.optString("content", "");
            if ("user".equals(role)) addBubble("你", content, true);
            else if ("assistant".equals(role) && !content.trim().isEmpty()) addBubble("km", content, false);
        }
        scrollToBottom();
    }

    private void addWelcome() {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(12), dp(34), dp(12), dp(28));

        TextView logo = label("km", 40, TEXT, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(rounded(PANEL_2, 25, BORDER));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(86), dp(86));
        logoLp.bottomMargin = dp(16);
        empty.addView(logo, logoLp);

        TextView title = label("有什么需要我帮你完成？", 19, TEXT, true);
        title.setGravity(Gravity.CENTER);
        empty.addView(title);

        TextView desc = label("我可以理解任务、调用工具，并在你授权后操作手机。", 12, MUTED, false);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(9), 0, dp(12));
        empty.addView(desc);

        LinearLayout feature = new LinearLayout(this);
        feature.setOrientation(LinearLayout.VERTICAL);
        feature.setPadding(dp(14), dp(12), dp(14), dp(12));
        feature.setBackground(rounded(PANEL_2, 16, BORDER));
        feature.addView(label("先配置你的模型", 13, TEXT, true));
        TextView hint = label("点击顶部 MODEL 卡片，填写 API 地址、模型和密钥。", 11, MUTED, false);
        hint.setPadding(0, dp(6), 0, 0);
        feature.addView(hint);
        empty.addView(feature, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chatColumn.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addBubble(String who, String text, boolean user) {
        LinearLayout message = new LinearLayout(this);
        message.setOrientation(LinearLayout.VERTICAL);
        message.setGravity(user ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(5), 0, dp(7));
        message.setLayoutParams(rowLp);

        TextView author = label(user ? "YOU" : "KM  ·  ASSISTANT", 9, MUTED, true);
        author.setPadding(dp(5), 0, dp(5), dp(4));
        message.addView(author);

        TextView bubble = label(text, 14, user ? Color.WHITE : TEXT, false);
        bubble.setTextIsSelectable(true);
        bubble.setLineSpacing(dp(2), 1.0f);
        bubble.setPadding(dp(13), dp(11), dp(13), dp(11));
        bubble.setBackground(rounded(user ? Color.rgb(45, 91, 168) : PANEL_2, 16,
                user ? 0 : BORDER));
        bubble.setMaxWidth(dp(310));
        message.addView(bubble, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chatColumn.addView(message);
        scrollToBottom();
    }

    private void addToolLog(String toolName, String details) {
        mainHandler.post(() -> {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(11), dp(9), dp(11), dp(9));
            row.setBackground(rounded(Color.rgb(23, 42, 49), 13, Color.rgb(40, 79, 82)));
            TextView title = label("⚙  TOOL · " + toolName, 10, GREEN, true);
            row.addView(title);
            TextView body = label(details, 11, MUTED, false);
            body.setPadding(0, dp(4), 0, 0);
            row.addView(body);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(3), dp(4), dp(32), dp(7));
            chatColumn.addView(row, lp);
            scrollToBottom();
        });
    }

    private void scrollToBottom() {
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void updateStatus(String text) {
        mainHandler.post(() -> {
            if (statusText != null) statusText.setText(text);
        });
    }

    private void updateBadges() {
        if (modelChip != null) {
            android.content.SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean configured = !p.getString("api_key", "").trim().isEmpty()
                    && !p.getString("model", "").trim().isEmpty();
            String model = p.getString("model", "尚未配置").trim();
            modelChip.setText(configured ? model : "点击配置模型");
            modelChip.setTextColor(configured ? GREEN : TEXT);
        }
        if (accessChip != null) {
            boolean enabled = KmAccessibilityService.isEnabled(this);
            accessChip.setText(enabled ? "● 手机操作已启用" : "○ 点击启用无障碍");
            accessChip.setTextColor(enabled ? GREEN : MUTED);
        }
    }

    private void sendMessage() {
        if (busy) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getString("api_key", "").trim().isEmpty()) {
            showSettings();
            updateStatus("请先配置模型 API");
            return;
        }

        if (currentSession == null) createNewSession();
        input.setText("");
        try {
            currentSession.messages.add(new JSONObject().put("role", "user").put("content", text));
        } catch (Exception ignored) { }
        if ("新会话".equals(currentSession.title)) {
            currentSession.title = text.length() > 22 ? text.substring(0, 22) + "…" : text;
        }
        currentSession.updatedAt = System.currentTimeMillis();
        addBubble("你", text, true);
        persistSessions();

        busy = true;
        sendButton.setText("■");
        sendButton.setBackground(rounded(RED, 15, 0));
        stopButton.setVisibility(View.VISIBLE);
        updateStatus("km 正在思考…");

        ChatSession active = currentSession;
        executor.execute(() -> {
            KmAgent agent = new KmAgent(this);
            currentAgent = agent;
            try {
                String answer = agent.run(active.messages, new KmAgent.ToolCallbacks() {
                    @Override
                    public boolean confirm(String toolName, String args) {
                        return askForToolApproval(toolName, args);
                    }

                    @Override
                    public void onStatus(String message) {
                        updateStatus(message);
                    }

                    @Override
                    public void onToolEvent(String toolName, String details) {
                        addToolLog(toolName, details);
                    }
                });
                mainHandler.post(() -> {
                    if (active == currentSession) addBubble("km", answer, false);
                    active.updatedAt = System.currentTimeMillis();
                    persistSessions();
                    finishTask("已完成");
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                mainHandler.post(() -> {
                    if (active == currentSession) addBubble("km · 错误", message, false);
                    active.updatedAt = System.currentTimeMillis();
                    persistSessions();
                    finishTask("请求结束");
                });
            } finally {
                currentAgent = null;
            }
        });
    }

    private void finishTask(String status) {
        busy = false;
        sendButton.setText("↑");
        sendButton.setBackground(rounded(BLUE, 15, 0));
        stopButton.setVisibility(View.GONE);
        updateStatus(status);
    }

    private void cancelCurrentTask() {
        KmAgent agent = currentAgent;
        if (agent != null) agent.cancel();
        updateStatus("已请求停止当前任务…");
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
                    .setTitle("km 请求执行操作")
                    .setMessage("工具：" + toolName + "\n参数：" + args
                            + "\n\n允许后才会执行这一步。")
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
        fields.setPadding(dp(17), dp(8), dp(17), dp(7));
        fields.setBackgroundColor(Color.rgb(16, 23, 37));

        TextView intro = label("连接任意 OpenAI Chat Completions 兼容服务。密钥仅保存在本机应用设置中。", 11, MUTED, false);
        intro.setPadding(0, 0, 0, dp(8));
        fields.addView(intro);

        EditText endpoint = settingsField("API 地址", prefs.getString("endpoint", "https://api.openai.com/v1/chat/completions"));
        EditText model = settingsField("模型名称", prefs.getString("model", "gpt-4o-mini"));
        EditText apiKey = settingsField("API Key", prefs.getString("api_key", ""));
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText customPrompt = settingsField("自定义系统提示词（可选）", prefs.getString("custom_prompt", ""));
        customPrompt.setMinLines(3);
        customPrompt.setMaxLines(5);
        fields.addView(endpoint);
        fields.addView(model);
        fields.addView(apiKey);
        fields.addView(customPrompt);

        Switch nativeTools = new Switch(this);
        nativeTools.setText("原生 Function Calling");
        nativeTools.setTextColor(TEXT);
        nativeTools.setChecked(prefs.getBoolean("native_tools", true));
        fields.addView(nativeTools);

        TextView promptPreview = label("查看当前系统提示词 →", 12, BLUE, true);
        promptPreview.setPadding(0, dp(12), 0, dp(6));
        promptPreview.setOnClickListener(v -> {
            saveSettings(endpoint, model, apiKey, customPrompt, nativeTools);
            showPromptPreview();
        });
        fields.addView(promptPreview);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("模型与 Agent 设置")
                .setView(scroll)
                .setPositiveButton("保存", null)
                .setNeutralButton("测试连接", null)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                saveSettings(endpoint, model, apiKey, customPrompt, nativeTools);
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                saveSettings(endpoint, model, apiKey, customPrompt, nativeTools);
                testConnection();
            });
        });
        dialog.show();
    }

    private EditText settingsField(String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setHintTextColor(MUTED);
        edit.setText(value);
        edit.setTextColor(TEXT);
        edit.setTextSize(13);
        edit.setPadding(dp(11), dp(7), dp(11), dp(7));
        edit.setBackground(rounded(PANEL_2, 12, BORDER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(9);
        edit.setLayoutParams(lp);
        return edit;
    }

    private void saveSettings(EditText endpoint, EditText model, EditText apiKey,
                              EditText customPrompt, Switch nativeTools) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("endpoint", endpoint.getText().toString().trim())
                .putString("model", model.getText().toString().trim())
                .putString("api_key", apiKey.getText().toString().trim())
                .putString("custom_prompt", customPrompt.getText().toString())
                .putBoolean("native_tools", nativeTools.isChecked())
                .apply();
        updateBadges();
        updateStatus("模型设置已保存");
    }

    private void testConnection() {
        updateStatus("正在测试模型连接…");
        executor.execute(() -> {
            try {
                KmAgent agent = new KmAgent(this);
                String result = agent.testConnection();
                mainHandler.post(() -> new AlertDialog.Builder(this)
                        .setTitle("模型连接成功")
                        .setMessage(result)
                        .setPositiveButton("好的", null)
                        .show());
                updateStatus("模型连接正常");
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                mainHandler.post(() -> new AlertDialog.Builder(this)
                        .setTitle("连接测试失败")
                        .setMessage(message)
                        .setPositiveButton("好的", null)
                        .show());
                updateStatus("模型连接失败");
            }
        });
    }

    private void showPromptPreview() {
        String prompt = KmPromptComposer.compose(this,
                getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("native_tools", true),
                getSharedPreferences(PREFS, MODE_PRIVATE).getString("custom_prompt", ""));
        TextView content = label(prompt, 11, TEXT, false);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("当前系统提示词")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void openAccessibilitySettings() {
        new AlertDialog.Builder(this)
                .setTitle("启用 km 手机操作")
                .setMessage("在 Android 无障碍设置中启用 km。服务只在工具需要时读取当前界面；点击、输入、滑动等动作仍会逐次请求确认。")
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

    private void showMoreMenu() {
        String[] items = {"会话管理", "查看系统提示词", "导出当前对话", "清空当前对话", "设备权限设置"};
        new AlertDialog.Builder(this)
                .setTitle("km")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: showSessions(); break;
                        case 1: showPromptPreview(); break;
                        case 2: exportCurrentSession(); break;
                        case 3: clearCurrentSession(); break;
                        case 4: openAccessibilitySettings(); break;
                    }
                })
                .show();
    }

    private void createNewSession() {
        if (busy) {
            updateStatus("请先停止当前任务，再新建会话");
            return;
        }
        currentSession = new ChatSession(UUID.randomUUID().toString(), "新会话");
        sessions.add(0, currentSession);
        while (sessions.size() > 12) sessions.remove(sessions.size() - 1);
        persistSessions();
        renderCurrentSession();
        updateStatus("已创建新会话");
    }

    private void showSessions() {
        if (sessions.isEmpty()) createNewSession();
        String[] names = new String[sessions.size()];
        for (int i = 0; i < sessions.size(); i++) {
            ChatSession s = sessions.get(i);
            names[i] = s.title + "   ·   " + s.messages.size() + " 条消息";
        }
        new AlertDialog.Builder(this)
                .setTitle("会话历史")
                .setItems(names, (dialog, which) -> {
                    currentSession = sessions.get(which);
                    persistSessions();
                    renderCurrentSession();
                    updateStatus("已切换会话");
                })
                .setPositiveButton("新建会话", (d, w) -> createNewSession())
                .setNegativeButton("关闭", null)
                .show();
    }

    private void clearCurrentSession() {
        if (currentSession == null) return;
        new AlertDialog.Builder(this)
                .setTitle("清空当前会话？")
                .setMessage("只会删除当前会话的本地聊天记录，不会删除其他会话。")
                .setPositiveButton("清空", (d, w) -> {
                    currentSession.messages.clear();
                    currentSession.title = "新会话";
                    currentSession.updatedAt = System.currentTimeMillis();
                    persistSessions();
                    renderCurrentSession();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportCurrentSession() {
        if (currentSession == null || currentSession.messages.isEmpty()) {
            updateStatus("当前会话没有可导出的消息");
            return;
        }
        StringBuilder text = new StringBuilder("km 对话导出\n\n");
        text.append(currentSession.title).append("\n\n");
        for (JSONObject item : currentSession.messages) {
            String role = item.optString("role", "");
            if ("user".equals(role)) text.append("你：");
            else if ("assistant".equals(role)) text.append("km：");
            else continue;
            text.append(item.optString("content", "")).append("\n\n");
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "km 对话记录");
        share.putExtra(Intent.EXTRA_TEXT, text.toString());
        startActivity(Intent.createChooser(share, "导出对话"));
    }

    private void loadSessions() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = prefs.getString(STORE_SESSIONS, "");
        if (!raw.isEmpty()) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    ChatSession session = new ChatSession(item.optString("id", UUID.randomUUID().toString()),
                            item.optString("title", "会话"));
                    session.updatedAt = item.optLong("updatedAt", System.currentTimeMillis());
                    JSONArray messages = item.optJSONArray("messages");
                    if (messages != null) {
                        int start = Math.max(0, messages.length() - 100);
                        for (int m = start; m < messages.length(); m++) {
                            JSONObject message = messages.optJSONObject(m);
                            if (message != null) session.messages.add(message);
                        }
                    }
                    sessions.add(session);
                }
            } catch (Exception ignored) { }
        }

        String activeId = prefs.getString(STORE_ACTIVE, "");
        for (ChatSession s : sessions) {
            if (s.id.equals(activeId)) {
                currentSession = s;
                break;
            }
        }
        if (currentSession == null && !sessions.isEmpty()) currentSession = sessions.get(0);

        if (currentSession == null) {
            ChatSession migrated = new ChatSession(UUID.randomUUID().toString(), "旧会话");
            String oldHistory = prefs.getString("history", "[]");
            try {
                JSONArray old = new JSONArray(oldHistory);
                for (int i = 0; i < old.length(); i++) {
                    JSONObject item = old.optJSONObject(i);
                    if (item != null) migrated.messages.add(item);
                }
            } catch (Exception ignored) { }
            currentSession = migrated;
            sessions.add(migrated);
            persistSessions();
        }
    }

    private void persistSessions() {
        if (currentSession != null) currentSession.updatedAt = System.currentTimeMillis();
        JSONArray array = new JSONArray();
        for (ChatSession s : sessions) {
            JSONObject item = new JSONObject();
            JSONArray messages = new JSONArray();
            for (int i = Math.max(0, s.messages.size() - 100); i < s.messages.size(); i++) {
                messages.put(s.messages.get(i));
            }
            try {
                item.put("id", s.id);
                item.put("title", s.title);
                item.put("updatedAt", s.updatedAt);
                item.put("messages", messages);
                array.put(item);
            } catch (Exception ignored) { }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(STORE_SESSIONS, array.toString())
                .putString(STORE_ACTIVE, currentSession == null ? "" : currentSession.id)
                .apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBadges();
    }

    @Override
    protected void onDestroy() {
        KmAgent agent = currentAgent;
        if (agent != null) agent.cancel();
        executor.shutdownNow();
        super.onDestroy();
    }
}
