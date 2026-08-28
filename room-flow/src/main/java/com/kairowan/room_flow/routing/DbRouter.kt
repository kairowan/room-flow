package com.kairowan.room_flow.routing

import androidx.room.RoomDatabase

interface DbRouter {
    fun readable(ctx: RouteContext = RouteContext()): RoomDatabase
    fun writable(ctx: RouteContext = RouteContext()): RoomDatabase
}
