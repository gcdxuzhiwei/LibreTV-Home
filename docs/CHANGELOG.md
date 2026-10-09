# 更新日志

## 1.1.3

修复切换页面、来源或重新搜索后旧封面任务继续排队的问题：取消旧任务并清理队列，阻止过期结果回写，保留 Android 6.0 兼容性。

## 1.1.2

修复播放器控制条隐藏时遥控器左右键快进 / 快退 10 秒、确定键暂停 / 继续，并消费抬起及长按重复事件，避免重复触发。

## 1.1.1

修复多来源搜索失败后的本页重试、返回列表时恢复未完成请求，以及影视源变更后的分类状态。

## 1.1.0

参考 Motion 示例的交互节奏，以 Android 原生动画实现：石墨黑背景、暖白标题、青柠焦点和淡紫点缀；遥控器焦点轻微放大上浮与回弹、确认按压反馈、页面与首批卡片错峰入场、海报淡入、首屏流光骨架，以及播放器工具栏渐隐。动画在系统关闭动画时退化为即时切换，骨架离开页面即停止。未增加网页运行时、远程动画资源或后端依赖。

参考：[Bobble hover](https://motion.dev/examples/react-bobble-hover)、[Smooth tabs](https://motion.dev/examples/vue-smooth-tabs)、[Skeleton Shimmer](https://motion.dev/examples/react-skeleton-shimmer)。本项目未复制 Motion+ 付费源码。
