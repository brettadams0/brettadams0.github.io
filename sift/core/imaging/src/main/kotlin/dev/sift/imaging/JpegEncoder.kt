package dev.sift.imaging

import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Baseline JPEG encoder with **4:4:4 chroma and optimised Huffman tables**
 * (§2.4, §6.11 step 3).
 *
 * ## Why this is hand-written
 *
 * §2.4 is one of the seven non-negotiables: default encoders use 4:2:0, which
 * halves colour resolution and shows as bleed on saturated edges — red fabric,
 * neon signage, foliage against sky. The spec's answer is OpenCV's
 * `IMWRITE_JPEG_SAMPLING_FACTOR_444` and `IMWRITE_JPEG_OPTIMIZE`.
 *
 * With OpenCV out of the dependency set, the platform alternative is
 * `Bitmap.compress`, which exposes a quality number and nothing else — whether
 * it subsamples is an undocumented implementation detail of whichever Skia build
 * ships on the device. That turns a non-negotiable requirement into a hope.
 * Writing the encoder makes it a fact, and an *asserted* fact:
 * `JpegEncoderTest` parses the SOF0 marker of real output and checks the
 * sampling factors are 1x1 on all three components.
 *
 * Optimised Huffman tables (the `IMWRITE_JPEG_OPTIMIZE` half) are a two-pass
 * encode: gather symbol frequencies, build tables fitted to this image, then
 * emit. Typically 3–8% smaller than the standard tables at identical quality —
 * which offsets a good part of what 4:4:4 costs.
 *
 * Baseline sequential, three components, no restart markers. Progressive and
 * arithmetic coding are out of scope: neither is universally supported and
 * neither affects image quality.
 */
object JpegEncoder {

    /**
     * Encode 8-bit interleaved RGB as a baseline JPEG.
     *
     * [quality] follows the IJG scale, matching `IMWRITE_JPEG_QUALITY`.
     */
    fun encode(rgb: ByteArray, width: Int, height: Int, quality: Int): ByteArray {
        require(rgb.size == width * height * 3) {
            "expected ${width * height * 3} bytes, got ${rgb.size}"
        }
        require(quality in 1..100) { "quality must be 1..100, was $quality" }

        val lumaTable = scaleTable(STD_LUMA_QUANT, quality)
        val chromaTable = scaleTable(STD_CHROMA_QUANT, quality)

        // ---- Colour transform, level shift, DCT, quantise ------------------
        val blocksWide = (width + 7) / 8
        val blocksHigh = (height + 7) / 8
        val blockCount = blocksWide * blocksHigh

        // 4:4:4 — every component is sampled at full resolution. No chroma
        // decimation happens anywhere in this function.
        val yBlocks = Array(blockCount) { IntArray(64) }
        val cbBlocks = Array(blockCount) { IntArray(64) }
        val crBlocks = Array(blockCount) { IntArray(64) }

        // Per-block and independent, so it parallelises cleanly. Entropy coding
        // below cannot: it is a single bit stream with a running DC predictor.
        Parallel.range(blocksHigh, blocksWide * 64) { by ->
            val yPatch = FloatArray(64)
            val cbPatch = FloatArray(64)
            val crPatch = FloatArray(64)
            for (bx in 0 until blocksWide) {
                for (row in 0 until 8) {
                    val sy = (by * 8 + row).coerceAtMost(height - 1)
                    for (col in 0 until 8) {
                        val sx = (bx * 8 + col).coerceAtMost(width - 1)
                        val s = (sy * width + sx) * 3
                        val r = (rgb[s].toInt() and 0xFF).toFloat()
                        val g = (rgb[s + 1].toInt() and 0xFF).toFloat()
                        val b = (rgb[s + 2].toInt() and 0xFF).toFloat()
                        val p = row * 8 + col
                        // JFIF full-range BT.601, then level shift by -128.
                        yPatch[p] = (0.299f * r + 0.587f * g + 0.114f * b) - 128f
                        cbPatch[p] = -0.168736f * r - 0.331264f * g + 0.5f * b
                        crPatch[p] = 0.5f * r - 0.418688f * g - 0.081312f * b
                    }
                }
                val index = by * blocksWide + bx
                forwardDctQuantise(yPatch, lumaTable, yBlocks[index])
                forwardDctQuantise(cbPatch, chromaTable, cbBlocks[index])
                forwardDctQuantise(crPatch, chromaTable, crBlocks[index])
            }
        }

        // ---- Pass 1: gather symbol frequencies ----------------------------
        val dcLumaFreq = IntArray(257)
        val acLumaFreq = IntArray(257)
        val dcChromaFreq = IntArray(257)
        val acChromaFreq = IntArray(257)

        run {
            var prevY = 0
            var prevCb = 0
            var prevCr = 0
            for (i in 0 until blockCount) {
                prevY = gatherBlock(yBlocks[i], prevY, dcLumaFreq, acLumaFreq)
                prevCb = gatherBlock(cbBlocks[i], prevCb, dcChromaFreq, acChromaFreq)
                prevCr = gatherBlock(crBlocks[i], prevCr, dcChromaFreq, acChromaFreq)
            }
        }

        val dcLuma = HuffmanTable.optimalFor(dcLumaFreq)
        val acLuma = HuffmanTable.optimalFor(acLumaFreq)
        val dcChroma = HuffmanTable.optimalFor(dcChromaFreq)
        val acChroma = HuffmanTable.optimalFor(acChromaFreq)

        // ---- Pass 2: emit --------------------------------------------------
        val out = ByteArrayOutputStream(width * height / 2 + 1024)
        writeMarker(out, 0xD8) // SOI
        writeApp0(out)
        writeQuantTables(out, lumaTable, chromaTable)
        writeFrameHeader(out, width, height)
        writeHuffmanTables(out, dcLuma, acLuma, dcChroma, acChroma)
        writeScanHeader(out)

        val bits = BitWriter(out)
        var prevY = 0
        var prevCb = 0
        var prevCr = 0
        for (i in 0 until blockCount) {
            prevY = encodeBlock(bits, yBlocks[i], prevY, dcLuma, acLuma)
            prevCb = encodeBlock(bits, cbBlocks[i], prevCb, dcChroma, acChroma)
            prevCr = encodeBlock(bits, crBlocks[i], prevCr, dcChroma, acChroma)
        }
        bits.flush()

        writeMarker(out, 0xD9) // EOI
        return out.toByteArray()
    }

    // ---- DCT ---------------------------------------------------------------

    /** `COS[u][x] = C(u)/2 · cos((2x+1)uπ/16)`, so a row transform is one matrix multiply. */
    private val COS: Array<FloatArray> = Array(8) { u ->
        FloatArray(8) { x ->
            val c = if (u == 0) (1.0 / sqrt(2.0)) else 1.0
            (c * 0.5 * cos((2 * x + 1) * u * Math.PI / 16.0)).toFloat()
        }
    }

    private val rowScratch = ThreadLocal.withInitial { FloatArray(64) }

    private fun forwardDctQuantise(patch: FloatArray, quant: IntArray, out: IntArray) {
        val tmp = rowScratch.get()
        // Rows
        for (y in 0 until 8) {
            val base = y * 8
            for (u in 0 until 8) {
                var acc = 0f
                val cu = COS[u]
                for (x in 0 until 8) acc += patch[base + x] * cu[x]
                tmp[base + u] = acc
            }
        }
        // Columns, quantise, and reorder into zig-zag in one pass.
        for (u in 0 until 8) {
            for (v in 0 until 8) {
                var acc = 0f
                val cv = COS[v]
                for (y in 0 until 8) acc += tmp[y * 8 + u] * cv[y]
                val natural = v * 8 + u
                out[ZIGZAG_INVERSE[natural]] = (acc / quant[natural]).roundToInt()
            }
        }
    }

    // ---- Entropy coding ----------------------------------------------------

    private fun magnitudeCategory(value: Int): Int {
        var v = if (value < 0) -value else value
        var category = 0
        while (v != 0) {
            v = v shr 1
            category++
        }
        return category
    }

    /** Frequency-gathering twin of [encodeBlock]. Returns the new DC predictor. */
    private fun gatherBlock(block: IntArray, prevDc: Int, dcFreq: IntArray, acFreq: IntArray): Int {
        val diff = block[0] - prevDc
        dcFreq[magnitudeCategory(diff)]++

        var run = 0
        for (k in 1 until 64) {
            val value = block[k]
            if (value == 0) {
                run++
                continue
            }
            while (run >= 16) {
                acFreq[ZRL]++
                run -= 16
            }
            acFreq[(run shl 4) or magnitudeCategory(value)]++
            run = 0
        }
        if (run > 0) acFreq[EOB]++
        return block[0]
    }

    private fun encodeBlock(
        bits: BitWriter,
        block: IntArray,
        prevDc: Int,
        dc: HuffmanTable,
        ac: HuffmanTable,
    ): Int {
        val diff = block[0] - prevDc
        val dcCategory = magnitudeCategory(diff)
        bits.write(dc.code(dcCategory), dc.length(dcCategory))
        if (dcCategory > 0) bits.write(signedBits(diff, dcCategory), dcCategory)

        var run = 0
        for (k in 1 until 64) {
            val value = block[k]
            if (value == 0) {
                run++
                continue
            }
            while (run >= 16) {
                bits.write(ac.code(ZRL), ac.length(ZRL))
                run -= 16
            }
            val category = magnitudeCategory(value)
            val symbol = (run shl 4) or category
            bits.write(ac.code(symbol), ac.length(symbol))
            bits.write(signedBits(value, category), category)
            run = 0
        }
        if (run > 0) bits.write(ac.code(EOB), ac.length(EOB))
        return block[0]
    }

    /** JPEG's ones-complement representation for negative coefficients. */
    private fun signedBits(value: Int, category: Int): Int =
        if (value >= 0) value else value + (1 shl category) - 1

    private const val EOB = 0x00
    private const val ZRL = 0xF0

    // ---- Quantisation tables ----------------------------------------------

    /** Annex K luminance table. */
    private val STD_LUMA_QUANT = intArrayOf(
        16, 11, 10, 16, 24, 40, 51, 61,
        12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,
        14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77,
        24, 35, 55, 64, 81, 104, 113, 92,
        49, 64, 78, 87, 103, 121, 120, 101,
        72, 92, 95, 98, 112, 100, 103, 99,
    )

    /** Annex K chrominance table. */
    private val STD_CHROMA_QUANT = intArrayOf(
        17, 18, 24, 47, 99, 99, 99, 99,
        18, 21, 26, 66, 99, 99, 99, 99,
        24, 26, 56, 99, 99, 99, 99, 99,
        47, 66, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
    )

    /** The IJG quality scale, so [quality] means the same as elsewhere. */
    private fun scaleTable(base: IntArray, quality: Int): IntArray {
        val scale = if (quality < 50) 5000 / quality else 200 - quality * 2
        return IntArray(64) { ((base[it] * scale + 50) / 100).coerceIn(1, 255) }
    }

    /** Natural order index -> zig-zag position. */
    private val ZIGZAG = intArrayOf(
        0, 1, 8, 16, 9, 2, 3, 10,
        17, 24, 32, 25, 18, 11, 4, 5,
        12, 19, 26, 33, 40, 48, 41, 34,
        27, 20, 13, 6, 7, 14, 21, 28,
        35, 42, 49, 56, 57, 50, 43, 36,
        29, 22, 15, 23, 30, 37, 44, 51,
        58, 59, 52, 45, 38, 31, 39, 46,
        53, 60, 61, 54, 47, 55, 62, 63,
    )

    private val ZIGZAG_INVERSE = IntArray(64).also { inverse ->
        for (z in 0 until 64) inverse[ZIGZAG[z]] = z
    }

    // ---- Segment writers ---------------------------------------------------

    private fun writeMarker(out: ByteArrayOutputStream, marker: Int) {
        out.write(0xFF)
        out.write(marker)
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeApp0(out: ByteArrayOutputStream) {
        writeMarker(out, 0xE0)
        writeShort(out, 16)
        out.write(byteArrayOf(0x4A, 0x46, 0x49, 0x46, 0x00)) // "JFIF\0"
        out.write(1); out.write(1) // version 1.1
        out.write(0) // no density units
        writeShort(out, 1); writeShort(out, 1)
        out.write(0); out.write(0) // no thumbnail
    }

    private fun writeQuantTables(out: ByteArrayOutputStream, luma: IntArray, chroma: IntArray) {
        writeMarker(out, 0xDB)
        writeShort(out, 2 + 2 * 65)
        out.write(0x00) // 8-bit precision, table 0
        for (z in 0 until 64) out.write(luma[ZIGZAG[z]])
        out.write(0x01) // 8-bit precision, table 1
        for (z in 0 until 64) out.write(chroma[ZIGZAG[z]])
    }

    private fun writeFrameHeader(out: ByteArrayOutputStream, width: Int, height: Int) {
        writeMarker(out, 0xC0) // SOF0, baseline
        writeShort(out, 8 + 3 * 3)
        out.write(8) // 8-bit samples
        writeShort(out, height)
        writeShort(out, width)
        out.write(3) // components
        // Sampling factors 1x1 on every component: this is 4:4:4 (§2.4).
        // Anything else here — 0x22 on luma being the usual 4:2:0 — is the bug
        // this encoder exists to make impossible.
        out.write(1); out.write(0x11); out.write(0) // Y,  quant table 0
        out.write(2); out.write(0x11); out.write(1) // Cb, quant table 1
        out.write(3); out.write(0x11); out.write(1) // Cr, quant table 1
    }

    private fun writeHuffmanTables(
        out: ByteArrayOutputStream,
        dcLuma: HuffmanTable,
        acLuma: HuffmanTable,
        dcChroma: HuffmanTable,
        acChroma: HuffmanTable,
    ) {
        writeHuffmanTable(out, 0x00, dcLuma)
        writeHuffmanTable(out, 0x10, acLuma)
        writeHuffmanTable(out, 0x01, dcChroma)
        writeHuffmanTable(out, 0x11, acChroma)
    }

    private fun writeHuffmanTable(out: ByteArrayOutputStream, id: Int, table: HuffmanTable) {
        writeMarker(out, 0xC4)
        writeShort(out, 2 + 1 + 16 + table.values.size)
        out.write(id)
        for (i in 1..16) out.write(table.bits[i])
        for (v in table.values) out.write(v)
    }

    private fun writeScanHeader(out: ByteArrayOutputStream) {
        writeMarker(out, 0xDA)
        writeShort(out, 6 + 2 * 3)
        out.write(3)
        out.write(1); out.write(0x00) // Y  -> DC 0, AC 0
        out.write(2); out.write(0x11) // Cb -> DC 1, AC 1
        out.write(3); out.write(0x11) // Cr -> DC 1, AC 1
        out.write(0); out.write(63); out.write(0) // baseline spectral selection
    }

    /** MSB-first bit writer with the mandatory 0xFF byte stuffing. */
    private class BitWriter(private val out: ByteArrayOutputStream) {
        private var buffer = 0
        private var count = 0

        fun write(value: Int, length: Int) {
            if (length == 0) return
            for (i in length - 1 downTo 0) {
                buffer = (buffer shl 1) or ((value shr i) and 1)
                count++
                if (count == 8) {
                    emit(buffer and 0xFF)
                    buffer = 0
                    count = 0
                }
            }
        }

        fun flush() {
            while (count != 0) {
                // Pad with 1 bits, per the standard.
                buffer = (buffer shl 1) or 1
                count++
                if (count == 8) {
                    emit(buffer and 0xFF)
                    buffer = 0
                    count = 0
                }
            }
        }

        private fun emit(byte: Int) {
            out.write(byte)
            // A 0xFF in entropy-coded data must be followed by 0x00 or a decoder
            // reads it as a marker and the rest of the file is garbage.
            if (byte == 0xFF) out.write(0x00)
        }
    }
}

/**
 * A Huffman table in JPEG's `BITS`/`HUFFVAL` form, with codes generated
 * canonically.
 *
 * [optimalFor] implements the length-limited construction from the JPEG
 * specification's Annex K.2 — the same algorithm libjpeg uses for
 * `optimize_coding`. The reserved extra symbol and the 16-bit limiting step are
 * both required by the format: a code longer than 16 bits cannot be expressed in
 * a `DHT` segment at all.
 */
internal class HuffmanTable(
    /** `bits[n]` = number of codes of length n, for n in 1..16. */
    val bits: IntArray,
    /** Symbols, ordered by increasing code length. */
    val values: IntArray,
) {
    private val codes = IntArray(256)
    private val lengths = IntArray(256)

    init {
        var code = 0
        var k = 0
        for (length in 1..16) {
            repeat(bits[length]) {
                val symbol = values[k]
                codes[symbol] = code
                lengths[symbol] = length
                code++
                k++
            }
            code = code shl 1
        }
    }

    fun code(symbol: Int): Int = codes[symbol]
    fun length(symbol: Int): Int = lengths[symbol]

    companion object {
        fun optimalFor(frequencies: IntArray): HuffmanTable {
            val freq = frequencies.copyOf(257)
            // One reserved code point that can never occur, so the all-ones code
            // is never assigned to a real symbol — required by the format.
            freq[256] = 1

            val codeSize = IntArray(257)
            val others = IntArray(257) { -1 }

            while (true) {
                var v1 = -1
                var least = Int.MAX_VALUE
                for (i in 0..256) {
                    if (freq[i] > 0 && freq[i] <= least) {
                        least = freq[i]
                        v1 = i
                    }
                }
                var v2 = -1
                least = Int.MAX_VALUE
                for (i in 0..256) {
                    if (freq[i] > 0 && freq[i] <= least && i != v1) {
                        least = freq[i]
                        v2 = i
                    }
                }
                if (v2 < 0) break

                freq[v1] += freq[v2]
                freq[v2] = 0

                var chain = v1
                codeSize[chain]++
                while (others[chain] >= 0) {
                    chain = others[chain]
                    codeSize[chain]++
                }
                others[chain] = v2

                chain = v2
                codeSize[chain]++
                while (others[chain] >= 0) {
                    chain = others[chain]
                    codeSize[chain]++
                }
            }

            val bits = IntArray(33)
            for (i in 0..256) {
                if (codeSize[i] > 0) bits[codeSize[i]]++
            }

            // Force every code to 16 bits or fewer.
            for (i in 32 downTo 17) {
                while (bits[i] > 0) {
                    var j = i - 2
                    while (bits[j] == 0) j--
                    bits[i] -= 2
                    bits[i - 1]++
                    bits[j + 1] += 2
                    bits[j]--
                }
            }

            // Drop the reserved symbol, which sorts last at the longest length.
            var last = 16
            while (last > 0 && bits[last] == 0) last--
            if (last > 0) bits[last]--

            // Ordered by the *unlimited* code size, over the full 1..32 range.
            // The limiting step above rebalances how many symbols sit at each
            // length but preserves both the total and this ordering, so walking
            // only 1..16 here would silently drop the symbols that started
            // longer than 16 bits and leave `values` shorter than `bits` claims.
            val values = ArrayList<Int>(256)
            for (length in 1..32) {
                for (symbol in 0..255) {
                    if (codeSize[symbol] == length) values.add(symbol)
                }
            }

            return HuffmanTable(bits.copyOf(17), values.toIntArray())
        }
    }
}
