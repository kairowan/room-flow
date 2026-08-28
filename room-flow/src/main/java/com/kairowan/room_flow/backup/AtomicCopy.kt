package com.kairowan.room_flow.backup

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/** 同目录临时文件 + sync + rename；copyBytes 仅供内部故障检查，不向公开 API 暴露测试开关。 */
internal fun atomicCopy(
    source: File,
    destination: File,
    beforeCommit: () -> Unit = {},
    copyBytes: (InputStream, OutputStream) -> Unit = { input, output -> input.copyTo(output) }
) {
    val temporary = File.createTempFile("roomflow-", ".tmp", destination.absoluteFile.parentFile)
    try {
        FileOutputStream(temporary).use { output ->
            source.inputStream().use { input -> copyBytes(input, output) }
            output.fd.sync()
        }
        beforeCommit()
        check(temporary.renameTo(destination)) { "无法原子替换目标文件，原文件保持不变" }
    } finally { temporary.delete() }
}
