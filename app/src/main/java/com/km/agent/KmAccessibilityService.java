package com.km.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class KmAccessibilityService extends AccessibilityService {
    private static volatile KmAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // KM reads the active window only in response to an explicit agent tool call.
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isEnabled(Context context) {
        if (instance != null) return true;
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName component = new ComponentName(context, KmAccessibilityService.class);
        String full = component.flattenToString();
        String shortName = component.flattenToShortString();
        for (String value : enabled.split(":")) {
            if (full.equalsIgnoreCase(value) || shortName.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    public static String getScreenTree() {
        KmAccessibilityService service = instance;
        if (service == null) return "无障碍服务未连接。请用户在系统无障碍设置中启用 km。";
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return "当前没有可读取的活动窗口。";
        StringBuilder out = new StringBuilder();
        int[] count = new int[] {0};
        appendNode(root, out, count, 0);
        return out.length() == 0 ? "当前界面没有可读取的控件文本。" : out.toString();
    }

    private static void appendNode(AccessibilityNodeInfo node, StringBuilder out, int[] count, int depth) {
        if (node == null || count[0] >= 180 || depth > 18) return;
        count[0]++;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String value = text == null ? "" : text.toString().trim();
        String description = desc == null ? "" : desc.toString().trim();
        if (!value.isEmpty() || !description.isEmpty() || node.isClickable() || node.isEditable()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            out.append(indent(depth))
                    .append("#").append(count[0])
                    .append(" class=").append(node.getClassName())
                    .append(" text=").append(quote(value))
                    .append(" desc=").append(quote(description))
                    .append(" clickable=").append(node.isClickable())
                    .append(" editable=").append(node.isEditable())
                    .append(" bounds=[").append(bounds.left).append(",").append(bounds.top)
                    .append("][").append(bounds.right).append(",").append(bounds.bottom).append("]")
                    .append("\n");
        }
        for (int i = 0; i < node.getChildCount() && count[0] < 180; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) appendNode(child, out, count, depth + 1);
        }
    }

    private static String indent(int depth) {\n        StringBuilder padding = new StringBuilder();\n        for (int i = 0; i < Math.min(depth, 10); i++) padding.append("  ");\n        return padding.toString();\n    }\n\n    private static String quote(String value) {
        if (value == null || value.isEmpty()) return "\"\"";
        String clean = value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
        if (clean.length() > 140) clean = clean.substring(0, 140) + "…";
        return "\"" + clean + "\"";
    }

    public static boolean tap(int x, int y) {
        KmAccessibilityService service = instance;
        if (service == null || Build.VERSION.SDK_INT < 24) return false;
        Path path = new Path();
        path.moveTo(x, y);
        return service.dispatchPath(path, 80);
    }

    public static boolean longPress(int x, int y, long duration) {
        KmAccessibilityService service = instance;
        if (service == null || Build.VERSION.SDK_INT < 24) return false;
        Path path = new Path();
        path.moveTo(x, y);
        return service.dispatchPath(path, Math.max(500, Math.min(duration, 5000)));
    }

    public static boolean swipe(int startX, int startY, int endX, int endY, long duration) {
        KmAccessibilityService service = instance;
        if (service == null || Build.VERSION.SDK_INT < 24) return false;
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);
        return service.dispatchPath(path, Math.max(150, Math.min(duration, 5000)));
    }

    private boolean dispatchPath(Path path, long duration) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                completed.set(true);
                latch.countDown();
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                completed.set(false);
                latch.countDown();
            }
        }, new Handler(Looper.getMainLooper()));
        if (!accepted) return false;
        try {
            return latch.await(6, TimeUnit.SECONDS) && completed.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static boolean clickText(String target) {
        KmAccessibilityService service = instance;
        if (service == null) return false;
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        AccessibilityNodeInfo found = findNode(root, target, false);
        if (found == null) return false;
        AccessibilityNodeInfo current = found;
        while (current != null) {
            if (current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            current = current.getParent();
        }
        return false;
    }

    public static boolean setText(String target, String value) {
        KmAccessibilityService service = instance;
        if (service == null) return false;
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        AccessibilityNodeInfo field = null;
        if (root != null) field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (field == null && target != null && !target.trim().isEmpty()) {
            field = findNode(root, target, true);
        }
        if (field == null || !field.isEditable()) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private static AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String target, boolean editableOnly) {
        if (node == null || target == null) return null;
        String needle = target.trim().toLowerCase();
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        boolean match = (text != null && text.toString().toLowerCase().contains(needle))
                || (desc != null && desc.toString().toLowerCase().contains(needle));
        if (match && (!editableOnly || node.isEditable())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findNode(node.getChild(i), target, editableOnly);
            if (found != null) return found;
        }
        return null;
    }

    public static boolean pressBack() {
        KmAccessibilityService service = instance;
        return service != null && service.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public static boolean pressHome() {
        KmAccessibilityService service = instance;
        return service != null && service.performGlobalAction(GLOBAL_ACTION_HOME);
    }
}
