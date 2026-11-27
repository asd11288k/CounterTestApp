# My Android App

这是一个可重复使用的 Android 原生项目模板。

## 自动构建 APK

向 `main` 分支提交代码后，GitHub Actions 会自动构建 Debug APK。

构建完成后，在仓库的 **Actions** 页面打开最新一次成功记录，在 **Artifacts** 中下载 `MyAndroidApp-debug`。

## 后续开发

新应用的界面和功能从 `app/src/main/java/com/example/myapp/MainActivity.java` 开始实现。
Android 项目配置和 `.github/workflows/build-apk.yml` 自动构建流程可以继续复用。
