package com.domm.projectreplay

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodec
import java.io.File
import java.nio.ByteBuffer

/**
 * Joins short H.264 MP4 segments by copying encoded samples into one MP4.
 * No re-encoding is performed, so export is relatively quick and preserves quality.
 */
object ClipAssembler {
    fun assemble(segments: List<File>, destination: File) {
        require(segments.isNotEmpty()) { "No replay segments available" }
        destination.parentFile?.mkdirs()
        var muxer: MediaMuxer? = null
        var outputTrack = -1
        var outputStarted = false
        var timelineOffsetUs = 0L
        try {
            muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            for (segment in segments) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(segment.absolutePath)
                    var videoTrack = -1
                    var videoFormat: MediaFormat? = null
                    for (track in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(track)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("video/")) {
                            videoTrack = track
                            videoFormat = format
                            break
                        }
                    }
                    require(videoTrack >= 0 && videoFormat != null) { "Replay segment has no video track" }
                    if (!outputStarted) {
                        outputTrack = muxer.addTrack(videoFormat)
                        muxer.start()
                        outputStarted = true
                    }
                    extractor.selectTrack(videoTrack)
                    val buffer = ByteBuffer.allocate(8 * 1024 * 1024)
                    val info = MediaCodec.BufferInfo()
                    var lastPresentationTimeUs = 0L
                    while (true) {
                        info.offset = 0
                        info.size = extractor.readSampleData(buffer, 0)
                        if (info.size < 0) break
                        info.presentationTimeUs = extractor.sampleTime + timelineOffsetUs
                        info.flags = extractor.sampleFlags
                        lastPresentationTimeUs = info.presentationTimeUs
                        muxer.writeSampleData(outputTrack, buffer, info)
                        extractor.advance()
                    }
                    val segmentDuration = if (videoFormat.containsKey(MediaFormat.KEY_DURATION))
                        videoFormat.getLong(MediaFormat.KEY_DURATION)
                    else lastPresentationTimeUs - timelineOffsetUs
                    timelineOffsetUs += maxOf(segmentDuration, lastPresentationTimeUs - (timelineOffsetUs) + 33_333L)
                } finally {
                    extractor.release()
                }
            }
            require(outputStarted && destination.exists() && destination.length() > 1024L) { "Clip export produced no video" }
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally {
            try { if (outputStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
