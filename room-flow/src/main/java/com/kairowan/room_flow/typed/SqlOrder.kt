package com.kairowan.room_flow.typed

class SqlOrder<E : Any> internal constructor(internal val column: EntityColumn<E, *>, internal val descending: Boolean)
