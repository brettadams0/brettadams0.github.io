package dev.sift.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sift.data.db.MediaAsset
import dev.sift.imaging.ColorSpaceTag
import dev.sift.imaging.FloatImage
import dev.sift.imaging.Orientation
import dev.sift.imaging.SourceMetadata
import dev.sift.model.LifecycleState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaStore ingest and export (§7, §6.11).
 */
@Singleton
class MediaStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * §7 — paginated, newest first, 200-row pages.
     *
     * The projection lists only what is needed and deliberately never includes
     * `DATA`: it is the deprecated raw filesystem path, it is unreadable under
     * scoped storage, and querying it costs a column of string allocation per
     * row for nothing.
     */
    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.MIME_TYPE,
    )

    /**
     * One page of the library.
     *
     * Paging goes through the **query-argument Bundle**, not through a
     * `"... LIMIT n OFFSET m"` suffix on the sort order. Appending SQL to the
     * sort string is the pre-Android-11 idiom and it is worse than deprecated
     * here: since API 30 MediaProvider parses that argument and rejects
     * anything containing SQL keywords, so the query returns **no rows at all**
     * rather than failing loudly. The result is an app that looks like it has an
     * empty camera roll.
     *
     * `QUERY_ARG_LIMIT` and `QUERY_ARG_OFFSET` are API 30, which is `minSdk`,
     * so there is no compatibility branch to keep.
     */
    fun page(limit: Int = PAGE_SIZE, offset: Int = 0): List<MediaAsset> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val queryArgs = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media._ID),
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }

        val assets = mutableListOf<MediaAsset>()
        resolver.query(collection, projection, queryArgs, null)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    // DATE_TAKEN is null for images with no EXIF; DATE_ADDED is
                    // in seconds. Clustering (§7) compares timestamps within a
                    // 10s window, so a unit mix-up here would silently stop every
                    // burst from collapsing.
                    val taken = cursor.getLong(takenColumn)
                        .takeIf { it > 0 }
                        ?: (cursor.getLong(addedColumn) * 1000L)

                    assets += MediaAsset(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        dateTaken = taken,
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        sizeBytes = cursor.getLong(sizeColumn),
                        mimeType = cursor.getString(mimeColumn) ?: "image/jpeg",
                        dHash = 0L,
                        clusterId = null,
                        analysisJson = null,
                        contentClass = null,
                        lifecycleState = LifecycleState.UNTRIAGED,
                        seenAt = null,
                    )
                }
            }
        return assets
    }

    /** §7 — `ContentObserver` for new captures. */
    fun observeChanges(): Flow<Unit> = callbackFlow {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(collection, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /**
     * Decode a source frame with its orientation already baked in (§6.1 steps
     * 1–2).
     *
     * Baking here rather than later is trap #2: move it downstream and every
     * crop, every face box and every aspect decision is computed against the
     * wrong axes.
     */
    /**
     * @param maxLongEdge decode no larger than this on the long edge, or null
     *   for full resolution.
     *
     *   Ingest passes a small value. §7 asks for "dHash and lightweight
     *   analysis" during the library scan, and the difference is not academic:
     *   a full-resolution decode of a 12MP frame costs a ~48MB bitmap and a
     *   ~144MB float buffer, and the scan was paying that for every photo in the
     *   library purely to derive a 64-bit hash and a content class. Sampling
     *   down first gives the same hash and the same routing decision for a
     *   fraction of the work. Grading still decodes at full resolution, because
     *   there the pixels are the point.
     */
    @Throws(IOException::class)
    fun decode(uri: Uri, maxLongEdge: Int? = null): DecodedFrame {
        val source = ImageDecoder.createSource(resolver, uri)
        var isP3 = false

        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false

            if (maxLongEdge != null) {
                val longEdge = maxOf(info.size.width, info.size.height)
                // Powers of two only: setTargetSampleSize rounds to one anyway,
                // and asking for an exact size makes the decoder do a scaling
                // pass instead of simply skipping samples.
                var sample = 1
                while (longEdge / (sample * 2) >= maxLongEdge) sample *= 2
                if (sample > 1) decoder.setTargetSampleSize(sample)
            }

            // §6.2 — read the source colour space; Samsung shoots sRGB or
            // Display P3 depending on settings.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val space = info.colorSpace
                isP3 = space != null && space.name.contains("Display P3", ignoreCase = true)
            }
        }

        val argb = IntArray(bitmap.width * bitmap.height)
        val safe = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
        safe.getPixels(argb, 0, safe.width, 0, 0, safe.width, safe.height)

        var image = FloatImage.fromArgb(safe.width, safe.height, argb)
        safe.recycle()
        if (safe !== bitmap) bitmap.recycle()

        val exif = readExif(uri)
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        ) ?: ExifInterface.ORIENTATION_NORMAL

        // Already tagged GAMMA_SRGB by fromArgb, and bake() carries the tag
        // through, so the frame arrives at §6.1 step 3 correctly labelled.
        image = Orientation.bake(image, orientation)

        val hasExposure = exif != null && (
            exif.getAttribute(ExifInterface.TAG_F_NUMBER) != null ||
                exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) != null
            )

        return DecodedFrame(
            image = image,
            metadata = SourceMetadata(
                hasExifExposure = hasExposure,
                isDisplayP3 = isP3,
                mimeType = null,
            ),
            sourceOrientation = orientation,
        )
    }

    data class DecodedFrame(
        val image: FloatImage,
        val metadata: SourceMetadata,
        val sourceOrientation: Int,
    )

    /**
     * §12 — corrupt EXIF must never fail a frame. A missing or unreadable EXIF
     * block costs metadata, not the photograph.
     */
    private fun readExif(uri: Uri): ExifInterface? = runCatching {
        resolver.openInputStream(uri)?.use { ExifInterface(it) }
    }.getOrNull()

    /**
     * §6.11 step 5 — insert into `Pictures/Sift` with `IS_PENDING` held during
     * the write, so a half-written file never appears in the gallery.
     */
    fun writeExport(
        jpeg: ByteArray,
        displayName: String,
        width: Int,
        height: Int,
        sourceUri: Uri?,
        /**
         * The original capture time, in epoch millis.
         *
         * **This is not the same thing as copying EXIF.** §2.5 requires the EXIF
         * block to carry over, and it does — but gallery apps do not sort by
         * EXIF. They sort by MediaStore's own `DATE_TAKEN` column, which the
         * system fills with "now" for any newly inserted row unless it is set
         * explicitly. Copying EXIF alone produces exports that are internally
         * correct and yet pile up at the top of the gallery dated today,
         * detached from the day they were actually shot.
         */
        dateTakenMillis: Long?,
    ): Uri? {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, EXPORT_RELATIVE_PATH)
            put(MediaStore.Images.Media.WIDTH, width)
            put(MediaStore.Images.Media.HEIGHT, height)
            put(MediaStore.Images.Media.IS_PENDING, 1)
            if (dateTakenMillis != null && dateTakenMillis > 0) {
                put(MediaStore.Images.Media.DATE_TAKEN, dateTakenMillis)
                // DATE_MODIFIED is in seconds, not millis. Setting it keeps the
                // export beside its original under "recently modified" sorts too.
                put(MediaStore.Images.Media.DATE_MODIFIED, dateTakenMillis / 1000)
            }
        }

        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { it.write(jpeg) } ?: return null
            if (sourceUri != null) copyExif(sourceUri, uri, width, height)
        } catch (e: IOException) {
            // Leave nothing half-written behind.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        } finally {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            runCatching { resolver.update(uri, values, null, null) }
        }
        return uri
    }

    /**
     * §2.5 / §6.11 step 4 — copy all EXIF, then override exactly three fields.
     *
     * Losing capture date, lens, exposure and GPS is unprofessional and
     * unrecoverable. The three overrides are required because the pixels changed:
     * orientation is now baked in (so re-applying it would rotate twice), the
     * software tag should say what produced the file, and the dimensions are new.
     *
     * `ACCESS_MEDIA_LOCATION` is what makes the GPS tags survive at all — without
     * it MediaStore silently redacts them and the loss is invisible for months
     * (trap #12).
     */
    private fun copyExif(sourceUri: Uri, destinationUri: Uri, width: Int, height: Int) {
        runCatching {
            val original = resolver.openInputStream(
                MediaStore.setRequireOriginal(sourceUri),
            )?.use { ExifInterface(it) } ?: return

            resolver.openFileDescriptor(destinationUri, "rw")?.use { descriptor ->
                val target = ExifInterface(descriptor.fileDescriptor)
                for (tag in COPIED_EXIF_TAGS) {
                    original.getAttribute(tag)?.let { target.setAttribute(tag, it) }
                }
                target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                target.setAttribute(ExifInterface.TAG_SOFTWARE, "Sift")
                target.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, width.toString())
                target.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, height.toString())
                target.saveAttributes()
            }
        }
    }

    /** §9.6 — refuse a batch below 2 GB free, with a clear message. */
    fun freeBytes(): Long = runCatching {
        val stat = StatFs(context.filesDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(Long.MAX_VALUE)

    fun hasSpaceForBatch(): Boolean = freeBytes() >= MIN_FREE_BYTES

    companion object {
        const val PAGE_SIZE = 200
        const val EXPORT_RELATIVE_PATH = "Pictures/Sift"

        /**
         * Long edge used for the library scan (§7).
         *
         * Large enough that the skin mask, edge density and bimodality still
         * classify content correctly, small enough that scanning a few thousand
         * photos is minutes rather than hours. Grading re-analyses at full
         * resolution, so nothing downstream inherits this approximation.
         */
        const val SCAN_LONG_EDGE = 1024

        /** §9.6 — holding original + graded + exports roughly triples footprint. */
        const val MIN_FREE_BYTES = 2L * 1024 * 1024 * 1024

        /** §9.6 — cap on the pending-review backlog. Spec calls 300 a guess. */
        const val PENDING_REVIEW_CAP = 300

        private val COPIED_EXIF_TAGS = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_METERING_MODE,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
        )
    }
}
