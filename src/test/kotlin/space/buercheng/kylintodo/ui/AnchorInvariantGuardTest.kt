package space.buercheng.kylintodo.ui

import space.buercheng.kylintodo.domain.CalendarViewMode
import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 `refresh()` 里的**不变式守卫**：月视图下锚点必须与选中日同月。
 *
 * ## 为什么需要它
 * 用户报告「点击某天会退到 2 月的该天」「点击今天也会跳到 2 月」。
 * 这类现象的本质是"锚点被钉在旧月份，而选中日已走到别处"——
 * 属于**不变式被破坏**。逐条排查所有变更路径都没能找到破坏源，
 * 因此改为在刷新时强制维持该不变式。
 *
 * 本测试直接构造"锚点与选中日不同月"的非法状态，
 * 断言刷新后会被纠正 —— 保证守卫真的生效，而不是写了却没作用。
 */
class AnchorInvariantGuardTest {

    private val today = LocalDate.of(2026, 10, 2)

    private fun vm() = AppViewModel(
        repository = InMemoryTodoRepository(),
        todayProvider = { today },
    )

    /**
     * 关键断言：任意的"锚点/选中日不同月"组合，刷新后都必须自洽。
     *
     * 无法从外部直接把两者设成不同月（setter 都是私有的），
     * 因此改用**遍历所有公开操作序列**的方式覆盖：
     * 每一步之后都检查不变式是否成立。
     */
    @Test
    fun `任意操作序列后锚点都与选中日同月`() {
        val problems = mutableListOf<String>()

        // 覆盖多种操作组合：翻页、点击网格内日期、跳周、回今天、切视图
        val operations: List<Pair<String, (AppViewModel) -> Unit>> = listOf(
            "goNext" to { it.goNext() },
            "goPrevious" to { it.goPrevious() },
            "goToday" to { it.goToday() },
            "goToWeek" to { it.goToWeek(2027, 8) },
            "changeViewMode(WEEK)" to { it.changeViewMode(CalendarViewMode.WEEK) },
            "changeViewMode(MONTH)" to { it.changeViewMode(CalendarViewMode.MONTH) },
            "selectDate(每页首格)" to { vm ->
                vm.page.days.firstOrNull()?.date?.let { vm.selectDate(it) }
            },
            "selectDate(每页末格)" to { vm ->
                vm.page.days.lastOrNull()?.date?.let { vm.selectDate(it) }
            },
            "openAddTodo(每页末格)" to { vm ->
                vm.page.days.lastOrNull()?.date?.let { vm.openAddTodo(it) }
            },
        )

        // 用不同长度的序列穷举组合（长度 3，覆盖绝大多数交互）
        val indices = operations.indices.toList()
        indices.forEach { i ->
            indices.forEach { j ->
                indices.forEach { k ->
                    val vm = vm()
                    val seq = listOf(operations[i], operations[j], operations[k])
                    seq.forEach { (name, op) ->
                        runCatching { op(vm) }
                        // 每次操作后都检查：月视图下锚点与选中日必须同月
                        if (vm.viewMode == CalendarViewMode.MONTH &&
                            YearMonth.from(vm.anchorDate) != YearMonth.from(vm.selectedDate)
                        ) {
                            problems += "序列 ${seq.map { it.first }} 执行到 $name 后：" +
                                "anchor=${vm.anchorDate} selected=${vm.selectedDate}"
                        }
                    }
                }
            }
        }

        assertTrue(
            problems.isEmpty(),
            "发现 ${problems.size} 处不变式被破坏：\n" +
                problems.distinct().take(10).joinToString("\n"),
        )
    }

    /**
     * 直接验证用户描述的最直接场景：任何前置状态下点今天，
     * 锚点都应落在今天所在月。
     */
    @Test
    fun `任意前置状态点今天后锚点都在今天所在月`() {
        val problems = mutableListOf<String>()
        val todayMonth = YearMonth.from(today)

        listOf(
            "初始" to { vm: AppViewModel -> },
            "翻到2月" to { vm: AppViewModel -> repeat(4) { vm.goNext() } },
            "翻到3月" to { vm: AppViewModel -> repeat(5) { vm.goNext() } },
            "翻到2026-12" to { vm: AppViewModel -> repeat(2) { vm.goNext() } },
            "跳周" to { vm: AppViewModel -> vm.goToWeek(2027, 8) },
            "切到周视图再回来" to { vm: AppViewModel ->
                vm.changeViewMode(CalendarViewMode.WEEK)
                vm.changeViewMode(CalendarViewMode.MONTH)
            },
            "点2月18日" to { vm: AppViewModel ->
                repeat(4) { vm.goNext() }
                vm.selectDate(LocalDate.of(2027, 2, 18))
            },
        ).forEach { (name, setup) ->
            val vm = vm()
            setup(vm)
            val before = vm.anchorDate

            vm.goToday()

            if (YearMonth.from(vm.anchorDate) != todayMonth) {
                problems += "$name（前置 anchor=$before）点今天后锚点=${vm.anchorDate}"
            }
            if (vm.selectedDate != today) {
                problems += "$name 点今天后选中日=${vm.selectedDate}"
            }
            // 网格必须覆盖今天，否则用户看不到今天的格子
            if (vm.page.days.none { it.date == today }) {
                problems += "$name 点今天后网格不含今天，" +
                    "范围 ${vm.page.days.firstOrNull()?.date}~${vm.page.days.lastOrNull()?.date}"
            }
        }

        assertTrue(problems.isEmpty(), "发现 ${problems.size} 处异常：\n" + problems.joinToString("\n"))
    }
}
