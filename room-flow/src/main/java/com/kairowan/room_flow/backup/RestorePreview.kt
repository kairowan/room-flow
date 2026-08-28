package com.kairowan.room_flow.backup

import java.io.File

data class RestorePreview(val identity: BackupIdentity, val bytes: Long, val recoveryFile: File?)
