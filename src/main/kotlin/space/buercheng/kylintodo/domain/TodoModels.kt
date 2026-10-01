package space.buercheng.kylintodo.domain

import java.time.LocalDate
import java.util.UUID

/**
 * 待办事项。
 *
 * 需求 5.1 定义的字段为 id / text / isCompleted / createdAt。
 * 另外补充 [date]：日历应用必须知道某条待办归属哪一天才能渲染到对应格子里，
 * 这是需求 3.1 F-03「日节点显示当天待办」所必需的。
 */
data class TodoItem(
    /** 唯一标识符（UUID） */
    val id: String = UUID.randomUUID().toString(),
    /** 待办内容 */
    val text: String,
    /** 完成状态，默认 false */
    val isCompleted: Boolean = false,
    /** 创建时间戳（毫秒），用于排序 */
    val createdAt: Long = System.currentTimeMillis(),
    /** 归属日期 —— 决定渲染到日历的哪一格 */
    val date: LocalDate,
) {
    companion object {
        /**
         * 校验并构造一条待办。
         *
         * 需求 3.2 异常流程要求：内容为空或纯空格时不保存。
         * 返回 null 表示非法输入，调用方据此禁用「添加」按钮。
         */
        fun createOrNull(text: String, date: LocalDate): TodoItem? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            return TodoItem(text = trimmed, date = date)
        }
    }
}

/**
 * 待办数据仓库。
 *
 * 抽象为接口以便在 UI 层注入不同实现（SQLite 实现 / 测试用内存实现）。
 */
interface TodoRepository {

    /** 读取指定日期的全部待办，按创建时间升序。 */
    fun findByDate(date: LocalDate): List<TodoItem>

    /** 读取指定日期区间内的待办数量统计，键为日期。 */
    fun countByDateRange(start: LocalDate, end: LocalDate): Map<LocalDate, Int>

    /** 新增一条待办。 */
    fun insert(item: TodoItem)

    /** 切换完成状态。 */
    fun setCompleted(id: String, completed: Boolean)

    /** 删除一条待办。 */
    fun delete(id: String)

    /** 释放资源。 */
    fun close() {}
}
