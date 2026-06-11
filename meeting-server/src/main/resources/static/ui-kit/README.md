# 智能会议 ui-kit

Swiss 浅色设计系统，供 `meeting-server` H5 与 `meeting-admin-server` 共用。

## 文件

| 文件 | 用途 |
|------|------|
| `tokens.css` | 色板、字体、间距、阴影；含 admin `--bg` 等别名 |
| `components.css` | 卡片、按钮、徽章、消息条、表格、表单 |
| `icons.css` / `icons.js` | Iconsax Linear 风格 SVG 注册表，`Iconsax.render(name)` |
| `motion.css` / `motion.js` | Shimmer、BlurFade、模块骨架，`SmMotion.initBlurFade()` |

## 同步

```powershell
.\ui-kit\sync-ui-kit.ps1
```

## 页面引用

```html
<link rel="stylesheet" href="ui-kit/tokens.css?v=sm-ui-20260610-1">
<link rel="stylesheet" href="ui-kit/components.css?v=sm-ui-20260610-1">
<link rel="stylesheet" href="ui-kit/icons.css?v=sm-ui-20260610-1">
<link rel="stylesheet" href="ui-kit/motion.css?v=sm-ui-20260610-1">
<script src="ui-kit/icons.js?v=sm-ui-20260610-1"></script>
<script src="ui-kit/motion.js?v=sm-ui-20260610-1"></script>
```

发版时统一 bump `?v=` 版本号。
