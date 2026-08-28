package com.kairowan.roomflow.legacy

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LegacyDao {
    @Insert
    fun insertUsers(users: List<LegacyUser>)

    @Insert
    fun insertRecords(records: List<LegacyRecord>)

    @Query("SELECT id, name, sex, lastActive FROM users ORDER BY id")
    fun users(): List<LegacyUser>

    @Query("UPDATE users SET name = :name WHERE id = :id")
    fun rename(id: Long, name: String): Int
}
