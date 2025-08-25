package com.kairowan.roomflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query


/**
 * @author 浩楠
 * @date 2025/8/25
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 *  描述: TODO
 */
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg users: User): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(list: List<User>): List<Long>

    @Query("UPDATE users SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("SELECT COUNT(*) FROM users")
    fun countAll(): Int
}