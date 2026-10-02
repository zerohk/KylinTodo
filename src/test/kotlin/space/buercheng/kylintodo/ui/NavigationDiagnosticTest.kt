package space.buercheng.kylintodo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

/**
 * 诊断用：定位"翻页在真实组件树里不生效"的断点。
 *
 * 不依赖 println —— Compose 测试框架会吞掉被测组件的标准输出，
 * 因此把执行轨迹写进 ViewModel 字段再断言。
 */
@OptIn(ExperimentalTestApi::class)
class NavigationDiagnosticTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 2)
    private var captured: AppViewModel? = null
    private val created = mutableListOf<Int>()

    /**
     * 注意这里必须用 `remember` 包住 ViewModel。
     *
     * 若直接构造，每次重组都会新建一个实例，测试持有的与界面渲染的
     * 可能不是同一个对象 —— 断言便会看到"状态没更新"的假象。
     * 生产代码（Main.kt）本来就用 remember，测试必须保持一致。
     */
    @Composable
    private fun Harness() {
        val vm = remember {
            AppViewModel(
                repository = InMemoryTodoRepository(),
                todayProvider = { today },
            ).also {
                captured = it
                created += it.instanceId
            }
        }
        MaterialTheme { CalendarScreen(viewModel = vm) }
    }

    private fun vm() = captured ?: error("ViewModel 未被捕获")

    @Test
    fun `直接调用 goNext 后锚点与轨迹`() {
        rule.setContent { Harness() }
        val vm = vm()
        val traceBefore = vm.navigateTrace

        rule.runOnUiThread { vm.goNext() }
        rule.waitForIdle()

        val traceAfter = vm.navigateTrace
        assertEquals(
            expected = true,
            actual = traceAfter != traceBefore,
            message = "navigate 应被调用",
        )
        assertEquals(
            expected = LocalDate.of(2026, 11, 2),
            actual = vm.anchorDate,
            message = "goNext 后锚点（轨迹：$traceAfter）",
        )
    }

    @Test
    fun `翻页后标题应显示新月份`() {
        rule.setContent { Harness() }
        rule.runOnUiThread { vm().goNext() }
        rule.waitForIdle()
        rule.onNodeWithText("2026 年 11 月").assertExists()
    }

    @Test
    fun `点击下一页按钮应触发翻页`() {
        rule.setContent { Harness() }
        val vm = vm()
        rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
        rule.waitForIdle()
        assertEquals(
            expected = LocalDate.of(2026, 11, 2),
            actual = vm.anchorDate,
            message = "点击后锚点（轨迹：${vm.navigateTrace}）",
        )
    }

    @Test
    fun `点击日期格子应改变选中日`() {
        rule.setContent { Harness() }
        val target = LocalDate.of(2026, 10, 20)
        rule.onNodeWithTag(UiTestTags.cell(target)).performClick()
        rule.waitForIdle()
        assertEquals(expected = target, actual = vm().selectedDate)
    }

    @Test
    fun `连续翻页三次应到达2027-01`() {
        rule.setContent { Harness() }
        repeat(3) {
            rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
            rule.waitForIdle()
        }
        assertEquals(
            expected = YearMonth.of(2027, 1),
            actual = YearMonth.from(vm().anchorDate),
        )
    }

    @Test
    fun `remember 保证只创建一个 ViewModel 实例`() {
        rule.setContent { Harness() }
        rule.waitForIdle()
        repeat(3) {
            rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
            rule.waitForIdle()
        }
        assertEquals(
            expected = 1,
            actual = created.size,
            message = "创建的实例序号：$created",
        )
    }
}
