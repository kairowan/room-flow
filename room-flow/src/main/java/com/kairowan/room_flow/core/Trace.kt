package com.kairowan.room_flow.core

/** 默认不输出日志。消息/标签必须由调用方脱敏；异常详情需显式开启。 */
object Trace {
    @Volatile
    private var logger: Logger = object : Logger {}

    internal fun setLogger(logger: Logger) { this.logger = logger }

    private inline fun log(block: (Logger) -> Unit) {
        try {
            block(logger)
        } catch (_: Exception) {
            // ponytail: 日志接收器故障不能让已提交事务被上层重试。
        }
    }

    fun d(tag: String, msg: String) = log { it.d(tag, msg) }
    fun w(tag: String, msg: String, tr: Throwable? = null) = log {
        it.w(tag, safeMessage(msg, tr), tr.takeIf { RoomFlowConfig.logExceptionDetails })
    }
    fun e(tag: String, msg: String, tr: Throwable? = null) = log {
        it.e(tag, safeMessage(msg, tr), tr.takeIf { RoomFlowConfig.logExceptionDetails })
    }

    private fun safeMessage(msg: String, tr: Throwable?): String =
        if (tr == null) msg else "$msg [${tr.javaClass.simpleName}]"

    inline fun <T> measure(tag: String, what: String, block: () -> T): T {
        val started = System.nanoTime()
        try {
            return block()
        } finally {
            d(tag, "$what: ${(System.nanoTime() - started) / 1_000_000.0} ms")
        }
    }
}
