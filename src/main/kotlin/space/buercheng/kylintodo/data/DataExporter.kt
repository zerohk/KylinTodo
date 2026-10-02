package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.domain.TodoItem

/**
 * 待办数据导出的**平台能力**。
 *
 * ## 为什么抽成接口
 * 序列化逻辑（[TodoExporter.toJson] / [TodoExporter.toCsv]）两端一致，
 * 但"写到哪"完全不同：
 *  - 桌面：弹原生目录选择框，用户可自选位置（需求 1）
 *  - Android：分区存储下应用无权写任意路径，须经 SAF 由用户指定
 *
 * 因此把"执行导出"抽象出来由界面层注入。附带好处是桌面导出逻辑
 * 可以在测试里替换为内存实现，不必真的弹对话框。
 */
interface DataExporter {

    /**
     * 执行一次导出。
     *
     * @return 可直接显示给用户的文本（含条数与文件位置）。
     *         失败时返回可读原因，**不抛异常** —— 导出失败不该让界面崩溃。
     */
    fun exportAll(items: List<TodoItem>): String
}
