package com.animewidget.maid

import org.json.JSONObject

/** Deterministic scope router and fact formatter for the small local model. */
object WeatherPromptRouter {
    enum class Scope { CURRENT, FUTURE, BOTH }

    data class RoutedPrompt(
        val scope: Scope,
        val facts: String,
        val factualLead: String,
        val fallbackAdvice: String
    )

    private val currentWords = listOf(
        "现在", "当前", "此刻", "眼下", "马上", "立刻", "目前",
        "出门", "穿什么", "怎么穿", "带伞", "要伞", "冷不冷", "热不热",
        "体感", "湿度", "风大", "温度", "几度"
    )
    private val futureWords = listOf(
        "未来", "之后", "以后", "稍后", "等会", "待会", "一会", "过会",
        "接下来", "小时后", "分钟后", "晚点", "几点", "下午", "晚上",
        "夜里", "明早", "预报", "还会", "什么时候", "持续", "转晴",
        "停雨", "升温", "降温"
    )
    private val factualPhrases = listOf(
        "天气晴好", "天气晴朗", "阳光明媚", "当前天气", "未来三小时",
        "毛毛雨", "小雨", "中雨", "大雨", "暴雨", "雷雨", "阵雨",
        "小雪", "中雪", "大雪", "多云", "阴天", "气温", "体感温度"
    )

    fun build(weatherJson: String, question: String): RoutedPrompt {
        val root = runCatching { JSONObject(weatherJson) }.getOrElse { JSONObject() }
        val scope = route(question)
        val currentWeather = value(root, "weather", "未知")
        val temperature = value(root, "temperature", "--")
        val current = buildString {
            append("地点：").append(value(root, "location", "未知")).append('；')
            append("当前天气：").append(currentWeather).append('；')
            append("温度：").append(temperature).append("℃；")
            append("体感：").append(value(root, "feels_like", "--")).append("℃；")
            append("湿度：").append(value(root, "humidity", "--")).append("%；")
            append("风速：").append(value(root, "wind_speed", "--")).append("；")
            append("最高/最低：").append(value(root, "daily_max", "--"))
                .append("℃/").append(value(root, "daily_min", "--")).append("℃。")
        }
        val futureItems = root.optJSONArray("hourly")
        val futureParts = mutableListOf<String>()
        if (futureItems != null) {
            for (index in 0 until minOf(3, futureItems.length())) {
                val item = futureItems.optJSONObject(index) ?: continue
                futureParts += buildString {
                    append(shortTime(value(item, "time", "未知时间")))
                    append(' ').append(value(item, "weather", "未知"))
                    append(' ').append(value(item, "temperature", "--")).append("℃")
                    append(" 降水概率").append(value(item, "rain_probability", "--")).append('%')
                }
            }
        }
        val future = if (futureParts.isEmpty()) "未来三小时数据暂缺。" else
            "未来三小时：" + futureParts.joinToString("；") + "。"
        val facts = when (scope) {
            Scope.CURRENT -> current
            Scope.FUTURE -> future
            Scope.BOTH -> current + "\n" + future
        }
        val lead = when (scope) {
            Scope.CURRENT -> "当前${currentWeather}，${temperature}℃。"
            Scope.FUTURE -> compactFutureLead(futureItems)
            Scope.BOTH -> "当前${currentWeather}，${temperature}℃；" +
                compactFutureLead(futureItems).removePrefix("未来")
        }
        return RoutedPrompt(scope, facts, lead, fallback(root, currentWeather))
    }

    fun userPrompt(route: RoutedPrompt, question: String): String =
        "【权威天气事实】\n${route.facts}\n【用户问题】\n${question.trim()}\n" +
            "只输出行动建议，不描述或重新判断天气、温度和时间。"

    fun combine(route: RoutedPrompt, generated: String): String {
        val clean = AiDialogueStore.limitAnswer(generated)
        val advice = clean.split(Regex("[。！？!？\\n]+"))
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { part -> factualPhrases.any(part::contains) }
            .joinToString("，")
            .trim('，', '。', '！', '？', ' ')
            .ifBlank { route.fallbackAdvice }
        return AiDialogueStore.limitAnswer(route.factualLead + advice + "。")
    }

    private fun route(question: String): Scope {
        val current = currentWords.any(question::contains)
        val future = futureWords.any(question::contains) ||
            Regex("\\d+\\s*(小时|分钟|点钟?)后").containsMatchIn(question)
        return when {
            current && !future -> Scope.CURRENT
            future && !current -> Scope.FUTURE
            else -> Scope.BOTH
        }
    }

    private fun compactFutureLead(items: org.json.JSONArray?): String {
        if (items == null || items.length() == 0) return "未来三小时数据暂缺。"
        val parts = mutableListOf<String>()
        for (index in 0 until minOf(3, items.length())) {
            val item = items.optJSONObject(index) ?: continue
            parts += shortTime(value(item, "time", "--")) +
                value(item, "weather", "未知") + value(item, "temperature", "--") + "℃"
        }
        return if (parts.isEmpty()) "未来三小时数据暂缺。" else
            "未来" + parts.joinToString("、") + "。"
    }

    private fun fallback(root: JSONObject, weather: String): String {
        val hourly = root.optJSONArray("hourly")
        var rainLikely = weather.contains("雨") || weather.contains("雷")
        if (hourly != null) {
            for (index in 0 until minOf(3, hourly.length())) {
                val item = hourly.optJSONObject(index) ?: continue
                if (item.optDouble("rain_probability", 0.0) >= 40.0 ||
                    item.optString("weather").contains("雨")
                ) rainLikely = true
            }
        }
        val temperature = root.optDouble("temperature", Double.NaN)
        return when {
            rainLikely -> "建议携带雨具，注意路面湿滑"
            weather.contains("雪") -> "注意保暖和道路结冰"
            temperature.isFinite() && temperature <= 8.0 -> "建议增加衣物并注意保暖"
            temperature.isFinite() && temperature >= 30.0 -> "建议防晒补水，避免久晒"
            else -> "建议按体感调整衣物，出行注意安全"
        }
    }

    private fun shortTime(raw: String): String {
        val match = Regex("(\\d{1,2}):(\\d{2})").findAll(raw).lastOrNull()
        return match?.groupValues?.get(1)?.trimStart('0')?.ifBlank { "0" }?.plus("时") ?: raw
    }

    private fun value(root: JSONObject, key: String, fallback: String): String {
        val raw = root.opt(key)
        return when {
            raw == null || raw == JSONObject.NULL -> fallback
            raw.toString().isBlank() -> fallback
            else -> raw.toString()
        }
    }
}
