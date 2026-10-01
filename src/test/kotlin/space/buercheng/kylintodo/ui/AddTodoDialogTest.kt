package space.buercheng.kylintodo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * 添加待办弹窗的交互测试（无头运行，无需真实鼠标）。
 *
 * 为什么需要这一层：Compose Desktop 的界面无法用合成鼠标事件可靠驱动 ——
 * .NET `mouse_event` 与 Java `AWT Robot` 实测均无效，而同样的 Robot 能成功
 * 点击普通 Swing 窗口。Compose 自带的测试框架直接操作语义树，绕开了真实
 * 输入设备，因此可以自动验证弹窗这类交互。
 *
 * 覆盖需求 3.2「添加待办」用例：
 *  - 内容为空或纯空格时，「添加」按钮保持禁用
 *  - 合法内容可提交，回调收到去除首尾空白后的文本
 *
 * 定位约定：输入框使用 [TODO_INPUT_TAG] 而非 placeholder 文本 ——
 * placeholder 在输入后即从语义树消失，靠它定位会让"输入之后"的断言失败。
 */
@OptIn(ExperimentalTestApi::class)
class AddTodoDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private val date = LocalDate.of(2026, 10, 1)

    /** 渲染弹窗，返回读取「关闭次数」的函数。 */
    private fun showDialog(onConfirm: (String) -> Unit = {}): () -> Int {
        var dismissCount = 0
        rule.setContent {
            MaterialTheme {
                AddTodoDialog(
                    date = date,
                    onDismiss = { dismissCount++ },
                    onConfirm = onConfirm,
                )
            }
        }
        return { dismissCount }
    }

    private fun input() = rule.onNodeWithTag(TODO_INPUT_TAG)
    private fun addButton() = rule.onNodeWithText("添加")
    private fun cancelButton() = rule.onNodeWithText("取消")

    // ---------------- 需求 3.2 异常流程：空内容不得保存 ----------------

    @Test
    fun `初始状态添加按钮禁用`() {
        showDialog()
        addButton().assertIsNotEnabled()
    }

    @Test
    fun `输入纯空格后添加按钮仍禁用`() {
        showDialog()
        input().performTextInput("    ")
        addButton().assertIsNotEnabled()
    }

    @Test
    fun `输入制表符与换行同样视为空白`() {
        showDialog()
        input().performTextInput("\t\n ")
        addButton().assertIsNotEnabled()
    }

    @Test
    fun `纯空白时点击添加不会回调`() {
        var received: String? = null
        showDialog { received = it }

        input().performTextInput("    ")
        addButton().performClick()
        rule.waitForIdle()

        assert(received == null) { "非法内容不应被提交，实际: $received" }
    }

    // ---------------- 需求 3.2 基本流程：合法内容可保存 ----------------

    @Test
    fun `输入合法内容后添加按钮可用`() {
        showDialog()
        input().performTextInput("买牛奶")
        addButton().assertIsEnabled()
    }

    @Test
    fun `点击添加会把去除首尾空白的内容回调出去并关闭弹窗`() {
        var received: String? = null
        val dismissCount = showDialog { received = it }

        input().performTextInput("  写需求文档  ")
        addButton().performClick()
        rule.waitForIdle()

        assert(received == "写需求文档") { "实际收到: $received" }
        assert(dismissCount() == 1) { "提交后应关闭弹窗" }
    }

    @Test
    fun `点击取消只关闭弹窗不回调内容`() {
        var received: String? = null
        val dismissCount = showDialog { received = it }

        input().performTextInput("不该被提交")
        cancelButton().performClick()
        rule.waitForIdle()

        assert(received == null) { "取消不应触发确认回调，实际: $received" }
        assert(dismissCount() == 1) { "取消应关闭弹窗" }
    }

    @Test
    fun `清空输入后添加按钮重新禁用`() {
        showDialog()
        input().performTextInput("临时内容")
        addButton().assertIsEnabled()

        // 清空必须用 performTextClearance()；performTextInput("") 不表示清空。
        input().performTextClearance()
        rule.waitForIdle()
        addButton().assertIsNotEnabled()
    }

    // ---------------- 展示信息 ----------------

    @Test
    fun `弹窗显示目标日期与星期`() {
        showDialog()
        // 让用户确认待办归属哪一天，避免加错日期
        rule.onNodeWithText("2026 年 10 月 1 日  星期四").assertExists()
    }

    @Test
    fun `弹窗标题为添加待办`() {
        showDialog()
        rule.onNodeWithText("添加待办").assertExists()
    }

    @Test
    fun `多行内容可正常输入并提交`() {
        var received: String? = null
        showDialog { received = it }

        input().performTextInput("第一行\n第二行")
        addButton().performClick()
        rule.waitForIdle()

        assert(received == "第一行\n第二行") { "实际收到: $received" }
    }
}
