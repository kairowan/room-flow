package com.kairowan.roomflow.data

import com.kairowan.room_flow.typed.PageResult
import com.kairowan.room_flow.typed.projection
import com.kairowan.room_flow.typed.select
import com.kairowan.room_flow.typed.update
import kotlinx.coroutines.flow.Flow

/** Optional business wrapper; borrows the host database and never closes/rebuilds it. */
class UserRepository(private val db: AppDatabase) {
    suspend fun adultSummaryPage(number: Int, size: Int, nameContains: String? = null): PageResult<UserSummary> =
        db.select(UserQueries.adults(nameContains))
            .project(projection(UserTable.id, UserTable.name, ::UserSummary))
            .pageResult(number, size)

    suspend fun adultPage(number: Int, size: Int, nameContains: String? = null): List<User> =
        db.select(UserQueries.adults(nameContains).page(number, size)).list()

    suspend fun adultCount(nameContains: String? = null): Long =
        db.select(UserQueries.adults(nameContains)).count()

    fun observeAdultPage(number: Int, size: Int, nameContains: String? = null): Flow<List<User>> =
        db.select(UserQueries.adults(nameContains).page(number, size)).observe()

    suspend fun rename(id: Long, name: String): Int {
        require(name.isNotBlank()) { "Name must not be blank" }
        return db.update(UserTable).set(UserTable.name, name).where(UserTable.id.eq(id)).execute()
    }
}
