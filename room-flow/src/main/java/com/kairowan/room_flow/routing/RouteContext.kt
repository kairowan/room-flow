package com.kairowan.room_flow.routing

/** 按用户、读写角色等维度描述路由请求。 */
data class RouteContext(
    val userId: String? = null,
    val role: Role = Role.READ,
    val hints: Map<String, Any?> = emptyMap()
)
