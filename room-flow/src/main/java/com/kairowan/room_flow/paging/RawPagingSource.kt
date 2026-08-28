package com.kairowan.room_flow.paging

import android.database.Cursor
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.kairowan.room_flow.core.RoomFlowConfig
import com.kairowan.room_flow.core.withBusyRetry
import com.kairowan.room_flow.sql.rawQuery
import com.kairowan.room_flow.sql.mapCancellable
import com.kairowan.room_flow.sql.withQueryCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** 单次分页代际；刷新必须创建新实例，调用方不能复用已失效的 Source。 */
class RawPagingSource<T : Any>(
    private val db: RoomDatabase,
    tables: Array<out String>,
    private val dispatcher: CoroutineDispatcher = RoomFlowConfig.ioDispatcher,
    private val countQueryProvider: (() -> SupportSQLiteQuery)? = null,
    private val queryProvider: (limit: Int, offset: Int) -> SupportSQLiteQuery,
    private val mapper: (Cursor) -> T
) : PagingSource<Int, T>() {
    private val observedTables = tables.copyOf()
    @Volatile
    private var observer: InvalidationTracker.Observer? = null

    init {
        require(tables.isNotEmpty() && tables.all { it.isNotBlank() }) { "必须指定分页观察表" }
        registerInvalidatedCallback { observer?.let { db.invalidationTracker.removeObserver(it) } }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? = state.anchorPosition?.let {
        val firstOffset = state.pages.firstOrNull()?.prevKey ?: 0
        val anchor = if (state.config.enablePlaceholders) it else firstOffset + it
        (anchor - state.config.initialLoadSize / 2).coerceAtLeast(0)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = try {
        withContext(dispatcher) {
            withBusyRetry {
                // ponytail: COUNT 与页面共用事务快照；复杂慢查询仍会占用事务通道，优先用 Room DAO PagingSource。
                db.withTransaction {
                    if (observer == null) {
                        val nextObserver = object : InvalidationTracker.Observer(observedTables) {
                            override fun onInvalidated(tables: Set<String>) { invalidate() }
                        }
                        observer = nextObserver
                        db.invalidationTracker.addObserver(nextObserver)
                        if (invalid) db.invalidationTracker.removeObserver(nextObserver)
                    }
                    if (invalid) return@withTransaction LoadResult.Invalid()
                    require(!params.placeholdersEnabled || countQueryProvider != null) { "占位符需要 COUNT 查询" }
                    val key = params.key ?: 0
                    require(key >= 0) { "分页 offset 不能为负数" }
                    val total = countQueryProvider?.let { provider ->
                        withQueryCancellation { signal -> db.rawQuery(provider(), signal).use { cursor ->
                            check(cursor.columnCount == 1 && cursor.moveToFirst() && !cursor.isNull(0) &&
                                cursor.getType(0) == Cursor.FIELD_TYPE_INTEGER) { "COUNT 必须返回一行非空整数" }
                            val count = cursor.getLong(0)
                            require(count in 0..Int.MAX_VALUE.toLong()) { "COUNT 超出 Int 范围" }
                            check(!cursor.moveToNext()) { "COUNT 不能返回多行" }
                            count.toInt()
                        } }
                    }
                    var offset = when (params) {
                        is LoadParams.Prepend -> (key - params.loadSize).coerceAtLeast(0)
                        is LoadParams.Refresh -> if (total == null) key else key.coerceAtMost((total - params.loadSize).coerceAtLeast(0))
                        is LoadParams.Append -> key
                    }
                    val limit = if (params is LoadParams.Prepend) key - offset else params.loadSize
                    suspend fun query(): List<T> = withQueryCancellation { signal ->
                        db.rawQuery(queryProvider(limit, offset), signal).use { cursor ->
                            cursor.mapCancellable(signal, mapper)
                        }
                    }
                    var items = query()
                    if (params is LoadParams.Refresh && total == null && offset > 0 && items.isEmpty()) {
                        offset = 0
                        items = query()
                    }
                    val end = Math.addExact(offset, items.size)
                    if (invalid) LoadResult.Invalid() else LoadResult.Page(
                        data = items,
                        prevKey = if (offset == 0) null else offset,
                        nextKey = if (items.size < limit || limit == 0 || (total != null && end >= total)) null else end,
                        itemsBefore = total?.let { offset.coerceAtMost(it) } ?: LoadResult.Page.COUNT_UNDEFINED,
                        itemsAfter = total?.let { (it - end).coerceAtLeast(0) } ?: LoadResult.Page.COUNT_UNDEFINED
                    )
                }
            }
        }
    } catch (cancelled: CancellationException) {
        // 已取消的加载代际不再复用，释放数据库对 observer/source 的引用。
        withContext(NonCancellable + dispatcher) { invalidate() }
        throw cancelled
    } catch (failure: Exception) {
        LoadResult.Error(failure)
    }
}
