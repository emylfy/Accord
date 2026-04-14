package org.akanework.gramophone.logic.utils

import android.os.Parcelable
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.Metadata
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlinx.parcelize.Parcelize
import java.io.File
import java.nio.charset.Charset
import kotlin.math.pow

object LrcUtils {

    private const val TAG = "LrcUtils"

    @Parcelize
    enum class Label(val isWalaoke: Boolean) : Parcelable {
        Male(true), // Walaoke
        Female(true), // Walaoke
        Duet(true), // Walaoke
        Background(false), // iTunes
        Voice1(false), // iTunes
        Voice2(false), // iTunes
        None(false)
    }

    @OptIn(UnstableApi::class)
    fun extractAndParseLyrics(
        metadata: Metadata,
        trim: Boolean
    ): MutableList<MediaStoreUtils.Lyric>? {
        for (i in 0..<metadata.length()) {
            val meta = metadata.get(i)
            val data =
                if (meta is VorbisComment && meta.key == "LYRICS") // ogg / flac
                    meta.value
                else if (meta is BinaryFrame && (meta.id == "USLT" || meta.id == "SYLT")) // mp3 / other id3 based
                    UsltFrameDecoder.decode(ParsableByteArray(meta.data))
                else if (meta is TextInformationFrame && (meta.id == "USLT" || meta.id == "SYLT")) // m4a
                    meta.values.joinToString("\n")
                else null
            val lyrics = data?.let {
                try {
                    parseLrcString(it, trim)
                } catch (e: Exception) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    null
                }
            }
            return lyrics ?: continue
        }
        return null
    }

    @OptIn(UnstableApi::class)
    fun loadAndParseLyricsFile(
        musicFile: File?,
        trim: Boolean
    ): MutableList<MediaStoreUtils.Lyric>? {
        val parent = musicFile?.parentFile ?: return null
        val baseName = musicFile.nameWithoutExtension
        // Try .lrc first, then .srt
        val lrcFile = File(parent, "$baseName.lrc")
        loadLrcFile(lrcFile)?.let {
            try {
                return parseLrcString(it, trim)
            } catch (e: Exception) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
        val srtFile = File(parent, "$baseName.srt")
        loadLrcFile(srtFile)?.let {
            try {
                return parseSrtString(it)
            } catch (e: Exception) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
        return null
    }

    private fun loadLrcFile(lrcFile: File?): String? {
        return try {
            if (lrcFile?.exists() == true)
                lrcFile.readBytes().toString(Charset.defaultCharset())
            else null
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: Log.getStackTraceString(e))
            null
        }
    }

    /*
     * Formats we have to consider in this method are:
     *  - Simple LRC files (ref Wikipedia) ex: [00:11.22] hello i am lyric
     *  - "compressed LRC" with >1 tag for repeating line ex: [00:11.22][00:15.33] hello i am lyric
     *  - Invalid LRC with all-zero tags [00:00.00] hello i am lyric
     *  - Lyrics that aren't synced and have no tags at all
     *  - Translations, type 1 (ex: pasting first japanese and then english lrc file into one file)
     *  - Translations, type 2 (ex: translated line directly under previous non-translated line)
     *  - The timestamps can variate in the following ways: [00:11] [00:11:22] [00:11.22] [00:11.222] [00:11:222]
     * In the future, we also want to support:
     *  - Extended LRC (ref Wikipedia) ex: [00:11.22] <00:11.22> hello <00:12.85> i am <00:13.23> lyric
     *  - Wakaloke gender extension (ref Wikipedia)
     *  - [offset:] tag in header (ref Wikipedia)
     * We completely ignore all ID3 tags from the header as MediaStore is our source of truth.
     */
    @VisibleForTesting
    fun parseLrcString(lrcContent: String, trim: Boolean): MutableList<MediaStoreUtils.Lyric> {
        val timeMarksRegex = "\\[(\\d+:\\d{2})([.:]\\d+)?]".toRegex()
        val wordTimeMarksRegex = "<(\\d+:\\d{2})([.:]\\d+)?>".toRegex()
        val labelRegex = "(?![\\d<])(\\d+|v\\d+|bg|F|M|D):(\\s?|.*:\\d)".toRegex()
        val labelRegexNumberOnly = "\\d+:\\s?".toRegex()
        val bgRegex = "\\[bg:\\s?(.*?)]".toRegex()
        val metadataRegex = "\\[[a-zA-Z#]+:[^]]*]".toRegex()

        // Multiline: merge lines without timestamps into the previous synced line
        val rawLines = lrcContent.lines()
        val mergedLines = mutableListOf<String>()
        for (line in rawLines) {
            if (timeMarksRegex.containsMatchIn(line) || metadataRegex.containsMatchIn(line) || mergedLines.isEmpty()) {
                mergedLines.add(line)
            } else if (line.isNotBlank()) {
                mergedLines[mergedLines.lastIndex] = mergedLines.last() + "\n" + line
            }
        }

        val list = mutableListOf<MediaStoreUtils.Lyric>()
        var currentLabel: Label
        var currentTimeStamp = -1L

        var firstLine = true
        var firstVoice: Int = -1
        // Add all lines found on LRC (probably will be unordered because of "compression" or translation type)
        mergedLines.forEach { line ->
            val label = labelRegex.find(
                line.replace(timeMarksRegex, "")
                    .replace(wordTimeMarksRegex, "")
                    .trim()
            ) ?: labelRegexNumberOnly.find(
                line.replace(timeMarksRegex, "")
                    .replace(wordTimeMarksRegex, "")
                    .trim()
            )

            if (firstLine || firstVoice == -1) {
                firstLine = false
                firstVoice = label?.let { parseSpeakerLabel(it.value).second } ?: -1
            }

            currentLabel =
                label?.let { parseSpeakerLabel(it.value, firstVoice).first } ?: Label.None

            timeMarksRegex.findAll(line).let { sequence ->
                if (sequence.count() == 0) {
                    return@let
                }
                val lyricLine = line.substring(sequence.last().range.last + 1)
                    .let { if (trim) it.trim() else it }
                    .let {
                        if ((currentLabel == Label.Voice1 || currentLabel == Label.Voice2) && !it.trim().startsWith("v")) {
                            it.replaceFirst(labelRegexNumberOnly, "")
                        } else it
                    }
                    .replace(labelRegex, "")
                sequence.forEach { match ->
                    val timeString = match.groupValues[1] + match.groupValues[2]
                    currentTimeStamp = parseTime(timeString)

                    if (wordTimeMarksRegex.containsMatchIn(lyricLine)) {
                        val wordMatches = wordTimeMarksRegex.findAll(lyricLine)
                        val words = lyricLine.split(wordTimeMarksRegex)
                        var lastWordTimestamp = currentTimeStamp
                        val wordTimestamps = words.mapIndexedNotNull { index, _ ->
                            wordMatches.elementAtOrNull(index)?.let { match ->
                                val wordTimestamp =
                                    parseTime(match.groupValues[1] + match.groupValues[2])
                                Triple(
                                    words.take(index + 1).sumOf { it.length },
                                    lastWordTimestamp,
                                    wordTimestamp
                                ).also {
                                    lastWordTimestamp = wordTimestamp
                                }
                            }
                        }.toMutableList().apply {
                            // Remove word timestamps whose content is empty
                            removeIf { it.first == 0 }
                        }
                        list.add(
                            MediaStoreUtils.Lyric(
                                startTimestamp = currentTimeStamp,
                                content = lyricLine.replace(wordTimeMarksRegex, ""),
                                wordTimestamps = wordTimestamps,
                                label = currentLabel
                            )
                        )
                    } else {
                        list.add(
                            MediaStoreUtils.Lyric(
                                startTimestamp = currentTimeStamp,
                                content = lyricLine,
                                label = currentLabel
                            )
                        )
                    }
                }
            }

            bgRegex.findAll(line).let { result ->
                if (result.count() == 0) {
                    return@let
                }
                result.forEach { match ->
                    currentLabel = Label.Background
                    val lyricLine = match.value.substring(4, match.value.length - 1).trim()
                    if (wordTimeMarksRegex.containsMatchIn(lyricLine)) {
                        val wordMatches = wordTimeMarksRegex.findAll(lyricLine)
                        val words = lyricLine.split(wordTimeMarksRegex)
                        var lastWordTimestamp = currentTimeStamp
                        val wordTimestamps = words.mapIndexedNotNull { index, _ ->
                            wordMatches.elementAtOrNull(index)?.let { match ->
                                val wordTimestamp =
                                    parseTime(match.groupValues[1] + match.groupValues[2])
                                Triple(
                                    words.take(index + 1).sumOf { it.length },
                                    lastWordTimestamp,
                                    wordTimestamp
                                ).also {
                                    lastWordTimestamp = wordTimestamp
                                }
                            }
                        }.toMutableList().apply {
                            // Remove word timestamps whose content is empty
                            removeIf { it.first == 0 }
                        }
                        list.add(
                            MediaStoreUtils.Lyric(
                                startTimestamp = currentTimeStamp + 1,
                                content = lyricLine.replace(wordTimeMarksRegex, ""),
                                wordTimestamps = wordTimestamps,
                                label = currentLabel
                            )
                        )
                    } else {
                        list.add(
                            MediaStoreUtils.Lyric(
                                startTimestamp = currentTimeStamp,
                                content = lyricLine,
                                label = currentLabel
                            )
                        )
                    }
                }
            }
        }

        // Sort and mark as translations all found duplicated timestamps (usually one)
        list.sortBy { it.startTimestamp }
        var previousTs = -1L
        var translationItems = intArrayOf()
        list.forEach {
            // Merge lyric and translation
            if (it.startTimestamp == previousTs && it.label != Label.Background) {
                list[list.indexOf(it) - 1].translationContent = it.content
                translationItems += list.indexOf(it)
            }
            previousTs = it.startTimestamp!!
        }
        // Remove translation items
        translationItems.reversed().forEach { list.removeAt(it) }

        // Add end timestamp to each item
        list.forEachIndexed { index, it ->
            if (it.wordTimestamps.isNotEmpty()) {
                it.endTimestamp = it.wordTimestamps.last().third
            } else {
                it.endTimestamp = list.getOrNull(index + 1)?.startTimestamp ?: Long.MAX_VALUE
            }
        }

        if (trim) {
            list.removeIf { it.content.isEmpty() }
        }

        // Add absolute position to each item
        list.takeWhile { it.content.isEmpty() }.forEach { _ -> list.removeAt(0) }
        var absolutePosition = 0
        list.forEachIndexed { index, it ->
            if (it.content.isNotEmpty() && it.label != Label.Background) {
                it.absolutePosition = absolutePosition
                absolutePosition++
            } else {
                it.absolutePosition = list[index - 1].absolutePosition
            }
        }
        if (list.isEmpty() && lrcContent.isNotEmpty()) {
            list.add(MediaStoreUtils.Lyric(content = lrcContent))
        }
        return list
    }

    private fun parseSpeakerLabel(labelContent: String, firstVoice: Int = -1): Pair<Label, Int?> {
        val numberRegex = "\\d+".toRegex()
        if (labelContent.contains(numberRegex)) {
            val label = numberRegex.find(labelContent) ?: throw IllegalArgumentException()

            // First line
            if (firstVoice == -1) {
                return Pair(Label.Voice1, label.value.toInt())
            }

            return if (label.value.toInt() > 1 && firstVoice > 1) {
                Pair(Label.Voice1, null)
            } else if (label.value.toInt() == 1 && firstVoice == 1) {
                Pair(Label.Voice1, null)
            } else if (label.value.toInt() > 2 && firstVoice == 1) {
                Pair(Label.Voice1, null)
            } else {
                Pair(Label.Voice2, null)
            }
        }

        return when {
            labelContent.startsWith("bg:") -> Pair(Label.Background, null)
            labelContent.startsWith("F:") -> Pair(Label.Female, null)
            labelContent.startsWith("M:") -> Pair(Label.Male, null)
            labelContent.startsWith("D:") -> Pair(Label.Duet, null)
            else -> Pair(Label.None, null)
        }
    }

    private fun parseSrtString(srtContent: String): MutableList<MediaStoreUtils.Lyric> {
        val list = mutableListOf<MediaStoreUtils.Lyric>()
        val timeRegex = "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})".toRegex()
        val blocks = srtContent.trim().split("\n\n", "\r\n\r\n")
        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size < 3) continue
            val timeMatch = timeRegex.find(lines[1]) ?: continue
            val hours = timeMatch.groupValues[1].toLong()
            val minutes = timeMatch.groupValues[2].toLong()
            val seconds = timeMatch.groupValues[3].toLong()
            val millis = timeMatch.groupValues[4].toLong()
            val timestamp = hours * 3600000 + minutes * 60000 + seconds * 1000 + millis
            val content = lines.subList(2, lines.size).joinToString("\n")
            list.add(MediaStoreUtils.Lyric(startTimestamp = timestamp, content = content))
        }
        if (list.isNotEmpty()) {
            list.add(0, MediaStoreUtils.Lyric())
        }
        return list
    }

    private fun parseTime(timeString: String): Long {
        val timeRegex = "(\\d+):(\\d{2})[.:](\\d+)".toRegex()
        val matchResult = timeRegex.find(timeString)

        val minutes = matchResult?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val seconds = matchResult?.groupValues?.get(2)?.toLongOrNull() ?: 0
        val millisecondsString = matchResult?.groupValues?.get(3)
        // if one specifies micro/pico/nano/whatever seconds for some insane reason,
        // scrap the extra information
        val milliseconds = (millisecondsString?.substring(
            0, millisecondsString.length.coerceAtMost(3)
        )?.toLongOrNull() ?: 0) * 10f.pow(3 - (millisecondsString?.length ?: 0)).toLong()

        return minutes * 60000 + seconds * 1000 + milliseconds
    }
}

// Class heavily based on MIT-licensed https://github.com/yoheimuta/ExoPlayerMusic/blob/77cfb989b59f6906b1170c9b2d565f9b8447db41/app/src/main/java/com/github/yoheimuta/amplayer/playback/UsltFrameDecoder.kt
// See http://id3.org/id3v2.4.0-frames
@OptIn(UnstableApi::class)
private class UsltFrameDecoder {
    companion object {
        private const val ID3_TEXT_ENCODING_ISO_8859_1 = 0
        private const val ID3_TEXT_ENCODING_UTF_16 = 1
        private const val ID3_TEXT_ENCODING_UTF_16BE = 2
        private const val ID3_TEXT_ENCODING_UTF_8 = 3

        fun decode(id3Data: ParsableByteArray): String? {
            if (id3Data.limit() < 4) {
                // Frame is malformed.
                return null
            }

            val encoding = id3Data.readUnsignedByte()
            val charset = getCharsetName(encoding)

            val lang = ByteArray(3)
            id3Data.readBytes(lang, 0, lang.size) // language
            val rest = ByteArray(id3Data.limit() - 4)
            id3Data.readBytes(rest, 0, rest.size)

            val descriptionEndIndex = indexOfEos(rest, 0, encoding)
            val textStartIndex = descriptionEndIndex + delimiterLength(encoding)
            val textEndIndex = indexOfEos(rest, textStartIndex, encoding)
            return decodeStringIfValid(rest, textStartIndex, textEndIndex, charset)
        }

        private fun getCharsetName(encodingByte: Int): Charset {
            return when (encodingByte) {
                ID3_TEXT_ENCODING_UTF_16 -> Charsets.UTF_16
                ID3_TEXT_ENCODING_UTF_16BE -> Charsets.UTF_16BE
                ID3_TEXT_ENCODING_UTF_8 -> Charsets.UTF_8
                ID3_TEXT_ENCODING_ISO_8859_1 -> Charsets.ISO_8859_1
                else -> Charsets.ISO_8859_1
            }
        }

        private fun indexOfEos(data: ByteArray, fromIndex: Int, encoding: Int): Int {
            var terminationPos = indexOfZeroByte(data, fromIndex)

            // For single byte encoding charsets, we're done.
            if (encoding == ID3_TEXT_ENCODING_ISO_8859_1 || encoding == ID3_TEXT_ENCODING_UTF_8) {
                return terminationPos
            }

            // Otherwise ensure an even index and look for a second zero byte.
            while (terminationPos < data.size - 1) {
                if (terminationPos % 2 == 0 && data[terminationPos + 1] == 0.toByte()) {
                    return terminationPos
                }
                terminationPos = indexOfZeroByte(data, terminationPos + 1)
            }

            return data.size
        }

        private fun indexOfZeroByte(data: ByteArray, fromIndex: Int): Int {
            for (i in fromIndex until data.size) {
                if (data[i] == 0.toByte()) {
                    return i
                }
            }
            return data.size
        }

        private fun delimiterLength(encodingByte: Int): Int {
            return if (encodingByte == ID3_TEXT_ENCODING_ISO_8859_1 || encodingByte == ID3_TEXT_ENCODING_UTF_8)
                1
            else
                2
        }

        private fun decodeStringIfValid(
            data: ByteArray,
            from: Int,
            to: Int,
            charset: Charset
        ): String {
            return if (to <= from || to > data.size) {
                ""
            } else String(data, from, to - from, charset)
        }
    }
}
