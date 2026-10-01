package space.buercheng.kylintodo.domain

import com.nlf.calendar.Solar
import com.nlf.calendar.util.HolidayUtil
import java.time.LocalDate

/**
 * 农历 / 节气 / 节假日数据源。
 *
 * 抽象为接口，便于单元测试注入确定性数据，也便于将来替换底层库。
 */
interface LunarService {
    /** 取得某一天的农历与节假日信息。 */
    fun describe(date: LocalDate): DayEnrichment
}

/**
 * 基于 `cn.6tail:lunar`（6tail/lunar-java）的实现。
 *
 * 选型依据与全部 API 验证过程见 `docs/技术选型-农历日期库.md`。
 *
 * ## 使用该库时必须注意的三点
 *  1. `monthInChinese` 对闰月**已自带**"闰"字（返回"闰二"），因此绝不能再拼一个"闰"，
 *     否则会得到 `闰闰二月初一` 这种乱码。
 *  2. 该库是 Java 库，类型为平台类型且无空注解，`HolidayUtil.getHoliday` 可能返回 null
 *     而编译器不会提示，因此必须显式判空。
 *  3. 节假日数据只覆盖 2001-12-29 ~ 2026-10-10；越界年份返回 null，语义即"普通日"，
 *     不会抛异常，因此未来年份会自动降级为正常显示。
 */
class LunarJavaService : LunarService {

    override fun describe(date: LocalDate): DayEnrichment {
        val solar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth)
        val lunar = solar.lunar

        // 节气：仅当当天恰为节气日时 jieQi 才非空，其余情况为空串。
        // 需求要求右下角显示"农历或节气"，故节气优先。
        val jieQi = lunar.jieQi?.takeIf { it.isNotBlank() }

        // 空格分隔的完整农历，供详情页显示
        val lunarFullText = buildString {
            append(lunar.yearInChinese).append('年')
            append(lunar.monthInChinese).append('月')
            append(lunar.dayInChinese)
        }

        val holiday = HolidayUtil.getHoliday(date.year, date.monthValue, date.dayOfMonth)
        val dayType = when {
            holiday == null -> DayType.NORMAL
            holiday.isWork -> DayType.WORKDAY   // 调休上班（班）
            else -> DayType.HOLIDAY              // 法定放假（休）
        }

        return DayEnrichment(
            lunarText = shortLunarLabel(lunar.dayInChinese, lunar.monthInChinese, jieQi),
            solarTerm = jieQi,
            lunarFullText = lunarFullText,
            dayType = dayType,
            holidayName = holiday?.name,
        )
    }

    /**
     * 格子右下角的短标签，节气优先于农历。
     *
     * 农历初一显示月名而非"初一"，这是中国日历的通行画法 ——
     * 否则整月都是"初一/初二/…"，看不出月份更替。
     *
     * `monthInChinese` 对闰月已自带"闰"字（如"闰二"），因此这里直接使用，
     * 绝不能再前置一个"闰"，否则会出现 `闰闰二月`。
     */
    private fun shortLunarLabel(
        dayInChinese: String,
        monthInChinese: String,
        jieQi: String?,
    ): String {
        if (jieQi != null) return jieQi
        if (dayInChinese == "初一") return "${monthInChinese}月"
        return dayInChinese
    }
}
