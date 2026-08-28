package com.kairowan.room_flow.typed

/** Opt in to compile-time EntityTable generation. Does not alter Room's schema or migrations. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RoomFlowEntity
