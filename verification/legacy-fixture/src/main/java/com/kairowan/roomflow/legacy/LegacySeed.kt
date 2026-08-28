package com.kairowan.roomflow.legacy

/** Frozen, synthetic edge values; never import current business SQL into historical fixtures. */
fun legacyUsers(): List<LegacyUser> = listOf(
    LegacyUser(1, "旧用户 ' OR 1=1 -- %_\\", "", 9_007_199_254_740_993),
    LegacyUser(7, "", "女", -1),
    LegacyUser(42, "用户🙂", "男", Long.MAX_VALUE)
)

fun seedLegacyDatabase(db: LegacyDatabase) {
    db.runInTransaction {
        db.legacyDao().insertUsers(legacyUsers())
        db.legacyDao().insertRecords(listOf(LegacyRecord(11, 1), LegacyRecord(12, 7), LegacyRecord(13, 42)))
    }
}
