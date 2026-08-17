package dev.cue.app.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.cue.capture.BoundingBox
import dev.cue.capture.RecognizedScreen
import dev.cue.capture.TextBlock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * §5.1. ML Kit Text Recognition v2, on-device, and the image discarded
 * immediately.
 *
 * §10: "OCR'd on-device, image discarded immediately, never stored." That is
 * implemented literally here — the bitmap is a local, it is recycled before this
 * function returns, and nothing downstream of [RecognizedScreen] can reach an
 * image because [RecognizedScreen] holds only text and rectangles.
 *
 * Blocks come back as lines rather than paragraphs. ML Kit's `Text.TextBlock`
 * groups by proximity, which on a chat screen sometimes fuses two bubbles from
 * different senders into one block — and a block is the unit §4.2 attributes, so
 * a fused block is a coin flip on the highest-stakes decision in the app. Lines
 * are smaller than a bubble, never larger than one, and [ConversationStitcher]
 * already reassembles them.
 */
class MlKitRecognizer(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(uri: Uri, id: String, capturedAt: Long): RecognizedScreen {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalArgumentException("Could not read the screenshot")

        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }

            val blocks = result.textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val bounds = line.boundingBox ?: return@mapNotNull null
                    TextBlock(
                        text = line.text,
                        bounds = BoundingBox(
                            left = bounds.left,
                            top = bounds.top,
                            right = bounds.right,
                            bottom = bounds.bottom,
                        ),
                    )
                }
            }

            return RecognizedScreen(
                id = id,
                screenWidth = bitmap.width,
                screenHeight = bitmap.height,
                blocks = blocks,
                capturedAt = capturedAt,
            )
        } finally {
            bitmap.recycle()
        }
    }
}
