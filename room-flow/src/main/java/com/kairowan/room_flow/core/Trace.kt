package com.kairowan.room_flow.core

/**
 * @author 浩楠
 *
 * @date 2025/8/24
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 * @Description: TODO  轻量级日志/耗时统计桥接类；库内统一走这里打印
 */
object Trace {
    interface Logger {
        fun d(tag: String, msg: String) {}
        fun w(tag: String, msg: String, tr: Throwable? = null) {}
        fun e(tag: String, msg: String, tr: Throwable? = null) {}
    }
    internal fun setLogger(l: Logger) { }

    fun d(tag: String, msg: String) {  }
    fun w(tag: String, msg: String, tr: Throwable? = null) {  }
    fun e(tag: String, msg: String, tr: Throwable? = null) { }

    inline fun <T> measure(tag: String, what: String, block: () -> T): T = block()
}