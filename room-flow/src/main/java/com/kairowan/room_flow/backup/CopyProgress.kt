package com.kairowan.room_flow.backup

import java.io.InputStream
import java.io.OutputStream

/** 回调和取消检查仅发生在发布前；完成字节数不是整个备份操作已经成功。 */
internal fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    total: Long,
    checkCancelled: () -> Unit,
    onProgress: ((Long, Long) -> Unit)? = null
) {
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    checkCancelled()
    onProgress?.invoke(0, total)
    while (true) {
        checkCancelled()
        val count = input.read(buffer)
        if (count == -1) break
        output.write(buffer, 0, count)
        copied = Math.addExact(copied, count.toLong())
        onProgress?.invoke(copied, total)
    }
    check(copied == total) { "复制期间源文件长度变化；请停止所有访问后重试" }
    checkCancelled()
}

internal fun requiredBackupBytes(sourceBytes: Long): Long {
    require(sourceBytes >= 0) { "文件大小不能为负数" }
    // ponytail: 保守预留两份主文件和 64 KiB 元数据，不承诺磁盘分配/并发写入后的可用空间。
    return Math.addExact(Math.multiplyExact(sourceBytes, 2), 64 * 1024L)
}
