package com.kairowan.roomflow.data

import com.kairowan.room_flow.typed.select

/** ponytail: 示例仅按唯一 id 降序向后翻页；不支持任意跳页，表失效后从第一页刷新。 */
internal suspend fun AppDatabase.queryUserPage(beforeId: Long?, size: Int): List<User> {
    val definition = UserQueries.matching()
        .whereIfNotNull(beforeId) { UserTable.id.lessThan(it) }
        .page(1, size)
    return select(definition).list()
}
