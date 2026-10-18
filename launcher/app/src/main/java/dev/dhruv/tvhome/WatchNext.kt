package dev.dhruv.tvhome

import android.content.Context
import android.database.Cursor
import android.media.tv.TvContract

class WatchItem(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val art: String?,
    val progress: Float,
    val intentUri: String?,
    val packageName: String?,
)

/**
 * Reads the system Watch Next ("Continue watching") list. Any failure — missing permission,
 * no TV provider, odd columns — yields an empty list so the row simply stays hidden.
 */
object WatchNext {
    private const val MAX_ITEMS = 10

    // Column names spelled out: Kotlin can't reach the constants inherited from Java interfaces
    // through TvContract.WatchNextPrograms.
    private val PROJECTION = arrayOf(
        "_id", "title", "episode_title", "season_display_number", "episode_display_number",
        "poster_art_uri", "thumbnail_uri", "last_playback_position_millis", "duration_millis",
        "intent_uri", "package_name",
    )

    fun query(context: Context): List<WatchItem> = try {
        context.contentResolver.query(
            TvContract.WatchNextPrograms.CONTENT_URI, PROJECTION, null, null,
            "last_engagement_time_utc_millis DESC",
        )?.use { read(it) } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun read(c: Cursor): List<WatchItem> {
        val out = ArrayList<WatchItem>()
        while (c.moveToNext() && out.size < MAX_ITEMS) {
            val title = c.str("title") ?: continue
            val season = c.str("season_display_number")
            val episode = c.str("episode_display_number")
            val epTitle = c.str("episode_title")
            val number = listOfNotNull(season?.let { "S$it" }, episode?.let { "E$it" }).joinToString(" ")
            val subtitle = listOf(number, epTitle.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
            val pos = c.long("last_playback_position_millis")
            val dur = c.long("duration_millis")
            out += WatchItem(
                id = c.long("_id"),
                title = title,
                subtitle = subtitle.ifBlank { null },
                art = c.str("poster_art_uri") ?: c.str("thumbnail_uri"),
                progress = if (dur > 0 && pos > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                intentUri = c.str("intent_uri"),
                packageName = c.str("package_name"),
            )
        }
        return out
    }

    private fun Cursor.str(col: String): String? {
        val i = getColumnIndex(col)
        return if (i < 0 || isNull(i)) null else getString(i)?.takeIf { it.isNotBlank() }
    }

    private fun Cursor.long(col: String): Long {
        val i = getColumnIndex(col)
        return if (i < 0 || isNull(i)) 0L else getLong(i)
    }
}
