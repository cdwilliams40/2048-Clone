package com.barnyardblitz.data

import android.content.Context
import com.barnyardblitz.engine.Json
import com.barnyardblitz.engine.Session
import java.io.File

/**
 * The farm save, kept in the app's private storage.
 *
 * That location needs no runtime permission, which is why the game asks for
 * none at all. Writes go to a temporary file first so a kill mid-write cannot
 * leave a half-written save behind.
 */
class SaveStore(context: Context) {

    private val file = File(context.filesDir, "farm.json")
    private val temp = File(context.filesDir, "farm.json.tmp")

    fun read(): String? = try {
        if (file.exists()) file.readText(Charsets.UTF_8) else null
    } catch (_: Exception) {
        null
    }

    fun write(session: Session): Boolean = try {
        temp.writeText(Json.encode(session.toJson()), Charsets.UTF_8)
        if (file.exists()) file.delete()
        temp.renameTo(file)
    } catch (_: Exception) {
        false
    }

    fun wipe() {
        try {
            file.delete()
            temp.delete()
        } catch (_: Exception) {
            // nothing to do
        }
    }
}
