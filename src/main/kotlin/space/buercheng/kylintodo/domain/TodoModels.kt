package space.buercheng.kylintodo.domain

import java.time.LocalDate
import java.util.UUID

/**
 * 待办的重要程度 / 优先级。
 *
 * 四级设计参考主流待办应用（Todoist、Microsoft To Do、滴答清单）：
 * 无优先级 + 低 / 中 / 高三档。数字 [level] 便于排序与持久化。
 */
enum class TodoPriority(val level: Int, val label: String) {
    /** 未指定优先级 */
    NONE(0, "无"),
    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高");

    companion object {
        /** 按持久化的数字还原，未知值回落到 [NONE]，避免脏数据导致崩溃。 */
        fun fromLevel(level: Int): TodoPriority =
            entries.firstOrNull { it.level == level } ?: NONE
    }
}

/**
 * 待办事项。
 *
 * 原始需求 5.1 定义的字段为 id / text / isCompleted / createdAt。
 * 后续扩展：
 *  - [date]：日历应用必须知道待办归属哪一天才能渲染到对应格子
 *  - [priority]：重要程度 / 优先级（需求反馈第 4 条）
 *  - [tags]：自由标签，可多个（需求反馈第 4 条）
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
    /** 重要程度，默认无 */
    val priority: TodoPriority = TodoPriority.NONE,
    /** 标签集合，去重且不含空白 */
    val tags: Set<String> = emptySet(),
) {
    /** 是否带有任何标签。 */
    val hasTags: Boolean get() = tags.isNotEmpty()

    /** 是否为高优先级（用于在日历格子上做醒目标识）。 */
    val isHighPriority: Boolean get() = priority == TodoPriority.HIGH

    companion object {
        /** 单个标签的最大长度，超出则截断，避免撑破界面或存储。 */
        const val MAX_TAG_LENGTH = 12

        /** 单条待办允许的最大标签数，防止误粘贴超长内容。 */
        const val MAX_TAG_COUNT = 6

        /**
         * 校验并构造一条待办。
         *
         * 需求 3.2 异常流程要求：内容为空或纯空格时不保存。
         * 返回 null 表示非法输入，调用方据此禁用「添加」按钮。
         */
        fun createOrNull(
            text: String,
            date: LocalDate,
            priority: TodoPriority = TodoPriority.NONE,
            tags: Collection<String> = emptyList(),
        ): TodoItem? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            return TodoItem(
                text = trimmed,
                date = date,
                priority = priority,
                tags = normalizeTags(tags),
            )
        }

        /**
         * 规范化标签集合。
         *
         * 规则：
         *  - 按分隔符拆开后去除首尾空白，丢弃空串
         *  - 去重（忽略大小写不敏感的场景在此不做，中文不涉及）
         *  - 单个标签超长则截断
         *  - 最多保留 [MAX_TAG_COUNT] 个
         *
         * 标签内**不允许**包含逗号（逗号是持久化分隔符），会被拆分。
         */
        fun normalizeTags(raw: Collection<String>): Set<String> =
            raw.asSequence()
                .flatMap { it.split(',', '，', ';', '；') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { if (it.length > MAX_TAG_LENGTH) it.take(MAX_TAG_LENGTH) else it }
                .distinct()
                .take(MAX_TAG_COUNT)
                .toSet()

        /** 把标签集合序列化为可持久化的字符串（逗号分隔）。 */
        fun tagsToStorage(tags: Set<String>): String = tags.joinToString(",")

        /** 从持久化字符串还原标签集合。 */
        fun tagsFromStorage(stored: String?): Set<String> =
            if (stored.isNullOrBlank()) emptySet() else normalizeTags(listOf(stored))
    }
}

/**
 * 待办数据仓库。
 *
 * 抽象为接口以便在 UI 层注入不同实现（SQLite 实现 / 测试用内存实现）。
 */
interface TodoRepository {

    /** 读取指定日期的全部待办，按优先级降序、创建时间升序。 */
    fun findByDate(date: LocalDate): List<TodoItem>

    /**
     * 读取指定日期区间内的待办，按日期与创建时间升序。
     *
     * 供「日详情弹窗」在自身范围（7 天）内展示已有待办使用。
     */
    fun findByDateRange(start: LocalDate, end: LocalDate): List<TodoItem>

    /** 读取全部待办，按日期升序，供导出使用。 */
    fun findAll(): List<TodoItem>

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
