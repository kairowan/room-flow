package com.kairowan.roomflow.data

import com.kairowan.room_flow.sql.rawQueryEach
import java.io.Writer
import org.json.JSONObject

/** JSON Lines 示例：Writer 由调用方关闭；先写临时文件，成功后才发布，失败时不能把部分文件当完整导出。 */
suspend fun AppDatabase.exportUsers(writer: Writer): Long = rawQueryEach(
    UserQueries.exportAll()
) { cursor ->
    val row = JSONObject()
        .put("id", cursor.getLong(0))
        .put("name", cursor.getString(1))
        .put("age", if (cursor.isNull(2)) JSONObject.NULL else cursor.getInt(2))
        .put("sex", cursor.getString(3))
        .put("lastActive", cursor.getLong(4))
    writer.write(row.toString())
    writer.write("\n")
}
