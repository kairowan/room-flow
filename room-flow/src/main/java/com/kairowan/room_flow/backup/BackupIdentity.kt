package com.kairowan.room_flow.backup

/** databaseId 由调用方提供稳定的库/账号标识；不能用密码、令牌或可变显示名。 */
data class BackupIdentity(val databaseId: String, val version: Int, val roomIdentityHash: String) {
    init {
        require(databaseId.isNotBlank() && databaseId.length <= 256 && '\u0000' !in databaseId)
        require(version > 0 && roomIdentityHash.matches(Regex("[0-9a-fA-F]{32}")))
    }
}
