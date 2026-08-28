package com.kairowan.room_flow.typed

/** Items and matching total from one Room transaction; page numbers start at 1. */
data class PageResult<T>(val items: List<T>, val total: Long, val page: Int, val pageSize: Int) {
    init {
        require(total >= 0 && page >= 1 && pageSize in 1..500 && items.size <= pageSize)
    }

    val hasNext: Boolean get() = page.toLong() * pageSize < total
}
