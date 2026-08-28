package com.kairowan.room_flow.core

/** 日志接收接口；消息中不得包含密钥、绑定值或用户数据。 */
interface Logger {
    fun d(tag: String, msg: String) {}
    fun w(tag: String, msg: String, tr: Throwable? = null) {}
    fun e(tag: String, msg: String, tr: Throwable? = null) {}
}
