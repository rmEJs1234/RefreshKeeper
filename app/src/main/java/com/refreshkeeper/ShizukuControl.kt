package com.refreshkeeper

import rikka.shizuku.Shizuku

/** Shizuku(shell 권한)로 주사율 설정값을 직접 고정/복원한다. */
object ShizukuControl {

    private const val CHECK =
        "echo min=$(settings get system min_refresh_rate) peak=$(settings get system peak_refresh_rate)"
    private const val APPLY =
        "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; $CHECK"
    private const val RESET =
        "settings delete system min_refresh_rate; settings delete system peak_refresh_rate; $CHECK"

    fun apply(): String = run(APPLY)
    fun reset(): String = run(RESET)

    private fun run(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        p.waitFor()
        (out + err).trim().ifEmpty { "완료" }
    } catch (t: Throwable) {
        "오류: ${t.message}"
    }
}
