package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.domain.HolidayImportResult

/**
 * 节假日数据的导入 / 模板导出能力。
 *
 * ## 为什么要抽成接口
 * 两端选文件的方式完全不同：
 *  - 桌面：`java.awt.FileDialog`（原生文件对话框）
 *  - Android：SAF（`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`）
 *
 * 但**解析逻辑完全一致**，都在 [HolidayImporter] 里，
 * 因此平台实现只需负责"拿到字节 / 写出字节"这一段。
 */
interface HolidayTransfer {

    /** 导出导入模板（.xlsx），返回可显示给用户的结果文本。 */
    fun exportTemplate(): String

    /**
     * 让用户选择文件并解析。
     *
     * @return 解析结果；用户取消选择时返回 null（调用方据此区分"取消"与"失败"）。
     */
    fun pickAndParse(): HolidayImportResult?
}
