package com.kairowan.room_flow.paging

import android.database.Cursor
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.kairowan.room_flow.core.RoomFlowConfig
import kotlinx.coroutines.CoroutineDispatcher

/** SQL 使用稳定的唯一排序；COUNT 与分页查询必须共享过滤条件，值使用绑定参数。 */
fun <T : Any> RoomDatabase.pagerFromRaw(
    pageSize: Int,
    vararg tables: String,
    dispatcher: CoroutineDispatcher = RoomFlowConfig.ioDispatcher,
    countQuery: String? = null,
    countQueryProvider: (() -> SupportSQLiteQuery)? = null,
    config: PagingConfig = PagingConfig(pageSize, enablePlaceholders = countQuery != null || countQueryProvider != null),
    queryProvider: (limit: Int, offset: Int) -> SimpleSQLiteQuery,
    mapper: (Cursor) -> T
): Pager<Int, T> {
    require(config.pageSize == pageSize) { "config.pageSize 必须与 pageSize 一致" }
    require(countQuery == null || countQueryProvider == null) { "只能指定一种 COUNT 查询" }
    require(!config.enablePlaceholders || countQuery != null || countQueryProvider != null) { "占位符需要 COUNT 查询" }
    val count = countQueryProvider ?: countQuery?.let { sql -> { SimpleSQLiteQuery(sql) } }
    return Pager(config) { RawPagingSource(this, tables, dispatcher, count, queryProvider, mapper) }
}
