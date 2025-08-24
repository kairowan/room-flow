package com.kairowan.room_flow

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * @author 浩楠
 *
 * @date 2025/8/24
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 * @Description: TODO 基于原生 SQL + Room 失效机制的 Paging3 辅助
 */
fun <T: Any> RoomDatabase.pagerFromRaw(
    pageSize: Int,
    vararg tables: String,
    dispatcher: CoroutineDispatcher = RoomFlowConfig.ioDispatcher,
    countQuery: String? = null,
    queryProvider: (limit: Int, offset: Int) -> SimpleSQLiteQuery,
    mapper: (android.database.Cursor) -> T
): Pager<Int, T> {
    val dbRef = this
    return Pager(PagingConfig(pageSize = pageSize, enablePlaceholders = countQuery != null)) {
        object : PagingSource<Int, T>() {
            /** 监听表失效，触发数据源失效。 */
            private val observer = object : InvalidationTracker.Observer(tables) {
                override fun onInvalidated(tables: Set<String>) {
                    invalidate()
                }
            }
            init { dbRef.invalidationTracker.addObserver(observer) }

            override fun getRefreshKey(state: PagingState<Int, T>): Int? = null

            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
                val page = params.key ?: 0
                val limit = params.loadSize
                val offset = page * pageSize
                return try {
                    val items = withContext(dispatcher) {
                        withBusyRetry {
                            dbRef.openHelper.readableDatabase
                                .query(queryProvider(limit, offset)).use { c ->
                                    val list = mutableListOf<T>()
                                    while (c.moveToNext()) list += mapper(c)
                                    list
                                }
                        }
                    }
                    val nextKey = if (items.size < limit) null else page + 1
                    val prevKey = if (page == 0) null else page - 1
                    LoadResult.Page(items, prevKey, nextKey)
                } catch (t: Throwable) {
                    LoadResult.Error(t)
                }
            }

            override val jumpingSupported: Boolean get() = false


            override fun invalidate() {
                super.invalidate()
                dbRef.invalidationTracker.removeObserver(observer)
            }
        }
    }
}