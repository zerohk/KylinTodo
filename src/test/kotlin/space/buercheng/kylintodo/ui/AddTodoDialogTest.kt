package space.buercheng.kylintodo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoPriority
import java.time.LocalDate

/**
 * 添加待办弹窗的交互测试（无头运行，无需真实鼠标）。
 *
 * 为什么需要这一层：Compose Desktop 的界面无法用合成鼠标事件可靠驱动 ——
 * .NET `mouse_event` 与 Java `AWT Robot` 实测均无效，而同样的 Robot 能成功
 * 点击普通 Swing 窗口。Compose 自带的测试框架直接操作语义树，绕开了真实
 * 输入设备，因此可以自动验证弹窗这类交互。
 *
 * 覆盖：
 *  - 需求 3.2：内容为空或纯空格时「添加」按钮禁用，合法内容可提交
 *  - 需求反馈第 4 条：优先级选择、标签添加与移除
 *
 * 定位约定：输入框使用 testTag 而非 placeholder 文本 —— placeholder 在输入
 * 内容后即从语义树消失，靠它定位会让"输入之后"的断言失败。
 */
@OptIn(ExperimentalTestApi::class)
class AddTodoDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private val date = LocalDate.of(2026, 10, 1)

    /** 记录最近一次提交收到的（文本、优先级、标签）。 */
    private data class Submission(
        val text: String,
        val priority: TodoPriority,
        val tags: Set<String>,
    )

    private fun showDialog(
        onConfirm: (String, TodoPriority, Set<String>) -> Unit = { _, _, _ -> },
    ): () -> Int {
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

    /** 常用组合：记录提交内容。 */
    private fun showRecording(): Pair<() -> Submission?, () -> Int> {
        var submission: Submission? = null
        val dismiss = showDialog { t, p, g -> submission = Submission(t, p, g) }
        return { submission } to dismiss
    }

    private fun input() = rule.onNodeWithTag(TODO_INPUT_TAG)
    private fun tagInput() = rule.onNodeWithTag(TODO_TAG_INPUT_TAG)
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
        val (submission, _) = showRecording()
        input().performTextInput("    ")
        addButton().performClick()
        rule.waitForIdle()
        assert(submission() == null) { "非法内容不应被提交" }
    }

    @Test
    fun `清空输入后添加按钮重新禁用`() {
        showDialog()
        input().performTextInput("临时内容")
        addButton().assertIsEnabled()
        // 清空必须用 performTextClearance()；performTextInput("") 不表示清空
        input().performTextClearance()
        rule.waitForIdle()
        addButton().assertIsNotEnabled()
    }

    // ---------------- 需求 3.2 基本流程 ----------------

    @Test
    fun `输入合法内容后添加按钮可用`() {
        showDialog()
        input().performTextInput("买牛奶")
        addButton().assertIsEnabled()
    }

    @Test
    fun `点击添加会回调去除首尾空白的内容并关闭弹窗`() {
        val (submission, dismissCount) = showRecording()
        input().performTextInput("  写需求文档  ")
        addButton().performClick()
        rule.waitForIdle()

        assert(submission()?.text == "写需求文档") { "实际: ${submission()}" }
        assert(dismissCount() == 1) { "提交后应关闭弹窗" }
    }

    @Test
    fun `点击取消只关闭弹窗不回调内容`() {
        val (submission, dismissCount) = showRecording()
        input().performTextInput("不该被提交")
        cancelButton().performClick()
        rule.waitForIdle()

        assert(submission() == null) { "取消不应触发确认回调" }
        assert(dismissCount() == 1) { "取消应关闭弹窗" }
    }

    @Test
    fun `多行内容可正常输入并提交`() {
        val (submission, _) = showRecording()
        input().performTextInput("第一行\n第二行")
        addButton().performClick()
        rule.waitForIdle()
        assert(submission()?.text == "第一行\n第二行") { "实际: ${submission()}" }
    }

    // ---------------- 需求反馈第 4 条：优先级 ----------------

    @Test
    fun `默认优先级为无`() {
        val (submission, _) = showRecording()
        input().performTextInput("默认优先级")
        addButton().performClick()
        rule.waitForIdle()
        assert(submission()?.priority == TodoPriority.NONE) { "实际: ${submission()}" }
    }

    @Test
    fun `四级优先级都可选择并正确回调`() {
        TodoPriority.entries.forEach { expected ->
            val (submission, _) = showRecording()
            input().performTextInput("优先级测试")
            rule.onNodeWithText(expected.label).performClick()
            rule.waitForIdle()
            addButton().performClick()
            rule.waitForIdle()
            assert(submission()?.priority == expected) {
                "选择「${expected.label}」后应回调 $expected，实际: ${submission()}"
            }
        }
    }

    // ---------------- 需求反馈第 4 条：标签 ----------------

    @Test
    fun `输入标签后回车可加入标签集合`() {
        val (submission, _) = showRecording()
        input().performTextInput("带标签的待办")
        tagInput().performTextInput("工作")
        // ImeAction.Done 触发 onDone -> addTagFromDraft
        tagInput().performImeAction()
        rule.waitForIdle()
        addButton().performClick()
        rule.waitForIdle()

        assert(submission()?.tags == setOf("工作")) { "实际: ${submission()}" }
    }

    @Test
    fun `一次输入多个分隔符可加入多个标签`() {
        val (submission, _) = showRecording()
        input().performTextInput("多标签")
        tagInput().performTextInput("工作,紧急；电话")
        tagInput().performImeAction()
        rule.waitForIdle()
        addButton().performClick()
        rule.waitForIdle()

        assert(submission()?.tags == setOf("工作", "紧急", "电话")) {
            "实际: ${submission()}"
        }
    }

    @Test
    fun `标签数量不超过上限`() {
        val (submission, _) = showRecording()
        input().performTextInput("超量标签")
        tagInput().performTextInput((1..20).joinToString(",") { "标签$it" })
        tagInput().performImeAction()
        rule.waitForIdle()
        addButton().performClick()
        rule.waitForIdle()

        assert(submission()!!.tags.size == TodoItem.MAX_TAG_COUNT) {
            "实际数量: ${submission()!!.tags.size}"
        }
    }

    @Test
    fun `移除标签后不再出现在提交内容中`() {
        val (submission, _) = showRecording()
        input().performTextInput("移除标签")
        tagInput().performTextInput("待移除")
        tagInput().performImeAction()
        rule.waitForIdle()

        // 标签胶囊上的关闭按钮带有 contentDescription
        rule.onNodeWithContentDescription("移除标签 待移除").performClick()
        rule.waitForIdle()
        addButton().performClick()
        rule.waitForIdle()

        assert(submission()!!.tags.isEmpty()) { "实际: ${submission()}" }
    }

    // ---------------- 展示信息 ----------------

    @Test
    fun `标题行显示目标日期与星期`() {
        showDialog()
        // 让用户确认待办归属哪一天，避免加错日期
        rule.onNodeWithText("10 月 1 日").assertExists()
        rule.onNodeWithText("周四", substring = true).assertExists()
    }

    @Test
    fun `各分区标题存在`() {
        showDialog()
        rule.onNodeWithText("待办内容").assertExists()
        rule.onNodeWithText("重要程度").assertExists()
        rule.onNodeWithText("标签", substring = true).assertExists()
    }
}
