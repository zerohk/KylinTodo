package space.buercheng.kylintodo.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import java.time.LocalDate

/**
 * 状态变更操作日志（仅调试用）。
 *
 * ## 为什么需要它
 * 用户报告「点击某天后月份跳到别的月份」。此前的调试栏记录的是**状态快照**，
 * 能看出"状态变成了什么"，但看不出**是哪个操作改的** —— 而当锚点被某条
 * 意料之外的路径改写时，这一区分是定位问题的关键。
 *
 * 这里记录每次状态变更操作（名称、参数、变更前后的锚点与选中日），
 * 于是「点 3 月 28 日 → 锚点变成 2 月 18 日」这种矛盾现象会直接暴露
 * 成两行相邻记录，一眼能看出是哪一步引入的。
 */
class ActionLog {

    private val _entries = mutableStateListOf<Entry>()

    /** 最近的操作，最新的在最后。 */
    val entries: List<Entry> get() = _entries

    data class Entry(
        val action: String,
        val anchorBefore: LocalDate,
        val anchorAfter: LocalDate,
        val selectedBefore: LocalDate,
        val selectedAfter: LocalDate,
        val result: String,
    ) {
        override fun toString(): String =
            "$action  anchor: $anchorBefore→$anchorAfter  " +
                "selected: $selectedBefore→$selectedAfter  $result"
    }

    fun record(
        action: String,
        anchorBefore: LocalDate,
        anchorAfter: LocalDate,
        selectedBefore: LocalDate,
        selectedAfter: LocalDate,
        result: String = "",
    ) {
        _entries += Entry(action, anchorBefore, anchorAfter, selectedBefore, selectedAfter, result)
        // 上限防止长时间运行后无限增长
        while (_entries.size > MAX_ENTRIES) _entries.removeAt(0)
    }

    fun clear() = _entries.clear()

    private companion object {
        /**
         * 上限刻意压到 8 条：调试栏高度会挤压日历网格，
         * 条数太多会让网格变得难以点击，反而干扰复现。
         * 8 条足够看清"点击前后各发生了什么"。
         */
        const val MAX_ENTRIES = 8
    }
}

/**
 * 当前的操作日志实例。
 *
 * 用 CompositionLocal 下发而不是逐层传参：写日志的调用点在 ViewModel 层，
 * 而展示在调试栏，两者之间隔了若干个组件；为避免污染生产签名，
 * 仅在调试栏启用时注入。
 */
val LocalActionLog = compositionLocalOf<ActionLog?> { null }
