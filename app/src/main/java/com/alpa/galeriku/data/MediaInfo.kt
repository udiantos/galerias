package com.alpa.galeriku.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class InfoLine(val label: String, val value: String, val header: Boolean = false)

/** Mengumpulkan metadata file: info dasar, EXIF (foto), dan info teknis (video). Jalankan di thread background. */
object MediaInfo {

    fun hasLocationAccess(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun ExifInterface.str(tag: String): String? =
        getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }

    fun load(ctx: Context, item: MediaItem): List<InfoLine> {
        val out = ArrayList<InfoLine>()
        fun add(label: String, value: String?) {
            if (!value.isNullOrBlank()) out.add(InfoLine(label, value))
        }
        fun header(text: String) = out.add(InfoLine(text, "", header = true))

        header("File")
        add("Nama", item.name)
        add("Lokasi", item.relativePath.ifBlank { item.folder })
        add("Jenis", ctx.contentResolver.getType(item.uri))
        add(
            "Ukuran",
            "${Formatter.formatShortFileSize(ctx, item.size)} (${"%,d".format(item.size)} byte)",
        )
        if (item.width > 0 && item.height > 0) {
            val mp = item.width.toLong() * item.height / 1_000_000.0
            add("Resolusi", "${item.width} × ${item.height}" + if (mp >= 0.1) "  (${"%.1f".format(mp)} MP)" else "")
        }
        add(
            "Tanggal",
            SimpleDateFormat("EEEE, d MMMM yyyy  HH:mm:ss", Locale.getDefault()).format(Date(item.takenMs)),
        )
        if (item.isVideo) add("Durasi", fmtDurationLong(item.durationMs))

        if (item.isVideo) readVideo(ctx, item, out) else readExif(ctx, item, out)
        return out
    }

    private fun fmtDurationLong(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun readExif(ctx: Context, item: MediaItem, out: MutableList<InfoLine>) {
        try {
            val hasLoc = hasLocationAccess(ctx)
            val source = if (hasLoc) {
                try {
                    MediaStore.setRequireOriginal(item.uri)
                } catch (e: Exception) {
                    item.uri
                }
            } else {
                item.uri
            }
            ctx.contentResolver.openInputStream(source)?.use { stream ->
                val exif = ExifInterface(stream)
                val lines = ArrayList<InfoLine>()
                fun add(label: String, value: String?) {
                    if (!value.isNullOrBlank()) lines.add(InfoLine(label, value))
                }

                val make = exif.str(ExifInterface.TAG_MAKE)
                val model = exif.str(ExifInterface.TAG_MODEL)
                val device = when {
                    make != null && model != null ->
                        if (model.startsWith(make, ignoreCase = true)) model else "$make $model"
                    else -> model ?: make
                }
                add("Perangkat", device)
                add("Lensa", exif.str(ExifInterface.TAG_LENS_MODEL))
                add("Waktu foto (EXIF)", exif.str(ExifInterface.TAG_DATETIME_ORIGINAL))

                val exp = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                if (exp > 0) {
                    add("Waktu eksposur", if (exp < 1) "1/${(1 / exp).roundToInt()} dtk" else "%.1f dtk".format(exp))
                }
                val f = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                if (f > 0) add("Bukaan", "f/%.1f".format(f))
                val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                if (iso > 0) add("ISO", iso.toString())
                val fl = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                if (fl > 0) {
                    val fl35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
                    add(
                        "Panjang fokus",
                        "%.1f mm".format(fl) + if (fl35 > 0) "  (setara $fl35 mm)" else "",
                    )
                }
                val flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
                if (flash >= 0) add("Flash", if ((flash and 1) == 1) "Menyala" else "Tidak menyala")
                add("Perangkat lunak", exif.str(ExifInterface.TAG_SOFTWARE))

                val ll = exif.latLong
                if (ll != null) {
                    add("Koordinat GPS", "%.6f, %.6f".format(ll[0], ll[1]))
                    val alt = exif.getAltitude(Double.NaN)
                    if (!alt.isNaN()) add("Ketinggian", "%.0f m".format(alt))
                }

                if (lines.isNotEmpty()) {
                    out.add(InfoLine("EXIF", "", header = true))
                    out.addAll(lines)
                }
            }
        } catch (e: Exception) {
            // file tanpa EXIF (misalnya PNG) atau tidak bisa dibaca: lewati
        }
    }

    private fun readVideo(ctx: Context, item: MediaItem, out: MutableList<InfoLine>) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, item.uri)
            val lines = ArrayList<InfoLine>()
            fun add(label: String, value: String?) {
                if (!value.isNullOrBlank()) lines.add(InfoLine(label, value))
            }
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let {
                add("Bitrate", "%.1f Mbps".format(it / 1_000_000.0))
            }
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.let {
                add("Frame rate", "%.0f fps".format(it))
            }
            add("Rotasi", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let { "$it°" })
            add("Format", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))
            val audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            if (audio != null) add("Audio", if (audio == "yes") "Ada" else "Tidak ada")
            if (lines.isNotEmpty()) {
                out.add(InfoLine("Video", "", header = true))
                out.addAll(lines)
            }
        } catch (e: Exception) {
            // metadata tidak terbaca: lewati
        } finally {
            try {
                r.release()
            } catch (ignored: Exception) {
            }
        }
    }
}
