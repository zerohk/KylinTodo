package space.buercheng.kylintodo.domain

import java.time.LocalDate

/**
 * 把「用户导入的节假日」叠加在内置数据之上。
 *
 * ## 优先级设计
 * 用户导入的规则**优先**于库内置数据。理由：
 *  - 库内置数据只覆盖到 2026-10-10，用户导入正是为了补齐后续年份
 *  - 用户既然明确导入了某天，就应以导入为准（可能是官方调休安排有调整）
 *
 * ## 名称的处理
 * 用户只填了「休/班」而没填名称时，**沿用内置数据的名称**而不是留空 ——
 * 例如用户只写「2026-10-01 休」，仍应显示「国庆节」，
 * 这样导入很小的一张表也能得到完整显示。
 */
class OverlayLunarService(
    private val base: LunarService,
    private val table: HolidayTable,
) : LunarService {

    override fun describe(date: LocalDate): DayEnrichment {
        val builtIn = base.describe(date)
        val rule = table.find(date) ?: return builtIn

        return builtIn.copy(
            dayType = if (rule.isWork) DayType.WORKDAY else DayType.HOLIDAY,
            // 用户填了名称就用用户的，否则回退到内置名称
            holidayName = rule.name.ifBlank { builtIn.holidayName },
        )
    }
}
