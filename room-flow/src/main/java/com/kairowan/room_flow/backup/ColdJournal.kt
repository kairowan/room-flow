package com.kairowan.room_flow.backup

import java.io.DataInputStream
import java.io.File

/** Only after every connection/process is stopped; never delete or recover the original journal. */
internal fun checkColdJournal(journal: File) {
    if (!journal.exists()) return
    check(journal.isFile) { "-journal 不是普通文件，拒绝复制/恢复" }
    if (journal.length() == 0L) return
    // ponytail: accept only the fully zeroed 28-byte PERSIST header, not every invalid/hot header.
    // SQLite performs recovery; supporting other journal states requires a separate offline recovery flow.
    check(journal.length() >= 28L) { "-journal 头不完整，拒绝复制/恢复" }
    val header = ByteArray(28)
    DataInputStream(journal.inputStream()).use { it.readFully(header) }
    check(header.all { it == 0.toByte() }) { "存在未处理的 -journal，拒绝复制/恢复" }
}
