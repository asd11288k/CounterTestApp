package com.km.agent;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class KmTools {
    private KmTools() {}

    public static String promptCatalogue(boolean nativeTools) {
        return "手机与工作区工具：\n"
                + "- get_screen：读取当前活动窗口的可见控件树、文本和屏幕坐标。\n"
                + "- tap(x,y)：点击屏幕坐标。\n"
                + "- long_press(x,y,duration_ms)：长按屏幕坐标。\n"
                + "- swipe(start_x,start_y,end_x,end_y,duration_ms)：滑动屏幕。\n"
                + "- click_text(text)：点击文本或内容描述匹配的控件。\n"
                + "- set_text(text,target_text?)：向当前焦点输入框或匹配输入框设置文本。\n"
                + "- press_back / press_home：执行 Android 返回或主页全局操作。\n"
                + "- open_app(query)：按应用名称或包名启动应用。\n"
                + "- list_workspace：列出 km 私有工作区文件。\n"
                + "- read_file(path)：读取 km 私有工作区内的 UTF-8 文本文件。\n"
                + "- write_file(path,content)：写入 km 私有工作区文件。\n"
                + "- delete_file(path)：删除 km 私有工作区文件。\n"
                + "坐标来自 get_screen 的 bounds；执行屏幕变更工具前先确认用户意图。";
    }

    public static JSONArray schemas() {
        JSONArray tools = new JSONArray();
        tools.put(function("get_screen", "读取当前屏幕的控件树、文本和坐标。不会点击或修改界面。", objectSchema(new String[0], new String[0])));
        tools.put(function("tap", "点击屏幕坐标。执行前需要用户确认。", objectSchema(
                new String[]{"x","y"}, new String[]{"x","y"})));
        tools.put(function("long_press", "长按屏幕坐标。执行前需要用户确认。", objectSchema(
                new String[]{"x","y","duration_ms"}, new String[]{"x","y"})));
        tools.put(function("swipe", "从起点滑动到终点。执行前需要用户确认。", objectSchema(
                new String[]{"start_x","start_y","end_x","end_y","duration_ms"},
                new String[]{"start_x","start_y","end_x","end_y"})));
        tools.put(function("click_text", "点击文本或内容描述包含指定文字的控件。执行前需要用户确认。", objectSchema(
                new String[]{"text"}, new String[]{"text"})));
        tools.put(function("set_text", "向当前焦点输入框或匹配输入框输入文本。执行前需要用户确认。", objectSchema(
                new String[]{"text","target_text"}, new String[]{"text"})));
        tools.put(function("press_back", "执行 Android 返回操作。执行前需要用户确认。", objectSchema(new String[0], new String[0])));
        tools.put(function("press_home", "返回 Android 主屏幕。执行前需要用户确认。", objectSchema(new String[0], new String[0])));
        tools.put(function("open_app", "按应用名称或包名启动 Android 应用。执行前需要用户确认。", objectSchema(
                new String[]{"query"}, new String[]{"query"})));
        tools.put(function("list_workspace", "列出 km 私有工作区中的文件和目录。", objectSchema(new String[0], new String[0])));
        tools.put(function("read_file", "读取 km 私有工作区中的 UTF-8 文本文件。", objectSchema(
                new String[]{"path"}, new String[]{"path"})));
        tools.put(function("write_file", "在 km 私有工作区创建或覆盖 UTF-8 文本文件。执行前需要用户确认。", objectSchema(
                new String[]{"path","content"}, new String[]{"path","content"})));
        tools.put(function("delete_file", "删除 km 私有工作区中的文件。执行前需要用户确认。", objectSchema(
                new String[]{"path"}, new String[]{"path"})));
        return tools;
    }

    private static JSONObject function(String name, String description, JSONObject parameters) {
        JSONObject fn = new JSONObject();
        JSONObject outer = new JSONObject();
        try {
            fn.put("name", name);
            fn.put("description", description);
            fn.put("parameters", parameters);
            outer.put("type", "function");
            outer.put("function", fn);
        } catch (Exception ignored) { }
        return outer;
    }

    private static JSONObject objectSchema(String[] names, String[] required) {
        JSONObject root = new JSONObject();
        JSONObject properties = new JSONObject();
        try {
            root.put("type", "object");
            for (String name : names) {
                JSONObject item = new JSONObject();
                if ("duration_ms".equals(name)) {
                    item.put("type", "integer");
                    item.put("description", "持续时间，单位毫秒");
                } else if (name.endsWith("_x") || name.endsWith("_y") || "x".equals(name) || "y".equals(name)) {
                    item.put("type", "integer");
                    item.put("description", "屏幕像素坐标");
                } else {
                    item.put("type", "string");
                    item.put("description", "参数：" + name);
                }
                properties.put(name, item);
            }
            root.put("properties", properties);
            JSONArray requiredArray = new JSONArray();
            for (String value : required) requiredArray.put(value);
            root.put("required", requiredArray);
            root.put("additionalProperties", false);
        } catch (Exception ignored) { }
        return root;
    }

    public static boolean requiresApproval(String name) {
        return "tap".equals(name) || "long_press".equals(name) || "swipe".equals(name)
                || "click_text".equals(name) || "set_text".equals(name)
                || "press_back".equals(name) || "press_home".equals(name)
                || "open_app".equals(name) || "write_file".equals(name) || "delete_file".equals(name);
    }

    public static String execute(Context context, String name, JSONObject args) {
        try {
            switch (name) {
                case "get_screen":
                    return result(true, KmAccessibilityService.getScreenTree());
                case "tap":
                    requireAccess();
                    return result(KmAccessibilityService.tap(args.getInt("x"), args.getInt("y")),
                            "tap 执行结果");
                case "long_press":
                    requireAccess();
                    return result(KmAccessibilityService.longPress(args.getInt("x"), args.getInt("y"),
                            args.optLong("duration_ms", 900)), "long_press 执行结果");
                case "swipe":
                    requireAccess();
                    return result(KmAccessibilityService.swipe(args.getInt("start_x"), args.getInt("start_y"),
                            args.getInt("end_x"), args.getInt("end_y"), args.optLong("duration_ms", 350)),
                            "swipe 执行结果");
                case "click_text":
                    requireAccess();
                    return result(KmAccessibilityService.clickText(args.getString("text")), "click_text 执行结果");
                case "set_text":
                    requireAccess();
                    return result(KmAccessibilityService.setText(args.optString("target_text", ""),
                            args.getString("text")), "set_text 执行结果");
                case "press_back":
                    requireAccess();
                    return result(KmAccessibilityService.pressBack(), "已请求 Android 返回操作");
                case "press_home":
                    requireAccess();
                    return result(KmAccessibilityService.pressHome(), "已请求 Android 主屏幕操作");
                case "open_app":
                    requireAccess();
                    return openApp(context, args.getString("query"));
                case "list_workspace":
                    return listWorkspace(context);
                case "read_file":
                    return readFile(context, args.getString("path"));
                case "write_file":
                    return writeFile(context, args.getString("path"), args.getString("content"));
                case "delete_file":
                    return deleteFile(context, args.getString("path"));
                default:
                    return error("未知工具：" + name);
            }
        } catch (Exception e) {
            return error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static void requireAccess() {
        if (!KmAccessibilityService.isEnabled(KmApp.context())) {
            throw new IllegalStateException("请先在 Android 无障碍设置中启用 km。");
        }
    }

    private static String openApp(Context context, String query) {
        PackageManager pm = context.getPackageManager();
        String needle = query.toLowerCase().trim();
        Intent launcher = new Intent(Intent.ACTION_MAIN, null);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcher, 0);
        for (ResolveInfo info : apps) {
            String label = info.loadLabel(pm).toString();
            String packageName = info.activityInfo.packageName;
            if (label.toLowerCase().contains(needle) || packageName.toLowerCase().contains(needle)) {
                Intent launch = pm.getLaunchIntentForPackage(packageName);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                    return result(true, "已启动 " + label + " (" + packageName + ")");
                }
            }
        }
        return error("没有找到名称或包名匹配的应用：" + query);
    }

    private static File workspace(Context context) {
        File root = new File(context.getFilesDir(), "km_workspace");
        if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("无法创建 km 工作区");
        return root;
    }

    private static File safeFile(Context context, String relative) throws Exception {
        if (relative == null || relative.trim().isEmpty()) throw new IllegalArgumentException("path 不能为空");
        File root = workspace(context).getCanonicalFile();
        File file = new File(root, relative).getCanonicalFile();
        if (!file.getPath().startsWith(root.getPath() + File.separator) && !file.equals(root)) {
            throw new SecurityException("路径超出 km 私有工作区范围");
        }
        return file;
    }

    private static String listWorkspace(Context context) {
        File root = workspace(context);
        String[] names = root.list();
        if (names == null || names.length == 0) return result(true, "工作区为空：" + root.getAbsolutePath());
        StringBuilder out = new StringBuilder("工作区：" + root.getAbsolutePath() + "\n");
        for (String name : names) {
            File child = new File(root, name);
            out.append(child.isDirectory() ? "[目录] " : "[文件] ")
                    .append(name).append(child.isFile() ? " (" + child.length() + " bytes)" : "").append("\n");
        }
        return result(true, out.toString());
    }

    private static String readFile(Context context, String path) throws Exception {
        File file = safeFile(context, path);
        if (!file.isFile()) return error("文件不存在：" + path);
        if (file.length() > 1024 * 1024) return error("单个文件读取上限为 1 MB");
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append("\n");
        }
        return result(true, out.toString());
    }

    private static String writeFile(Context context, String path, String content) throws Exception {
        if (content.length() > 1024 * 1024) return error("单次写入上限为 1 MB");
        File file = safeFile(context, path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return error("无法创建父目录");
        try (FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return result(true, "已写入 " + file.getAbsolutePath());
    }

    private static String deleteFile(Context context, String path) throws Exception {
        File file = safeFile(context, path);
        if (!file.exists()) return error("文件不存在：" + path);
        if (file.isDirectory()) return error("当前版本只允许删除文件，不允许递归删除目录");
        return result(file.delete(), "删除文件：" + path);
    }

    private static String result(boolean success, String message) {
        JSONObject json = new JSONObject();
        try {
            json.put("success", success);
            json.put("result", message);
        } catch (Exception ignored) { }
        return json.toString();
    }

    private static String error(String message) {
        JSONObject json = new JSONObject();
        try {
            json.put("success", false);
            json.put("error", message);
        } catch (Exception ignored) { }
        return json.toString();
    }
}
