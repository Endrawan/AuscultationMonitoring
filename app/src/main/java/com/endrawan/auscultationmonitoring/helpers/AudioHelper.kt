package com.endrawan.auscultationmonitoring.helpers

import android.media.AudioFormat
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class AudioHelper(
    val channelConfig: Int,
    val sampleRateInHz: Int,
    val minShortBufferSize: Int
    ) {
    private val TAG = "AudioHelper"


    @Throws(IOException::class)
    fun pcmToWav(pcmFilePath: File, wavFilePath: File) {
        //The original pcm data size does not contain (file header). To add a file header, use
        val pcmLength: Long
        //The total size of the file (including the file header). To add a file header
        //Channel ID (1 (single channel) or 2 (dual channel), for adding file header)
        val channels = if (channelConfig == AudioFormat.CHANNEL_OUT_MONO) 1 else 2
        //Sample rate, to add a file header
        val sampleRate: Int = sampleRateInHz
        //Information transfer rate = ((sampling rate * number of channels * number of bits of each value) / 8), to add a file header
        val byteRate = sampleRate * channels * 16 / 8
        val data = ByteArray(minShortBufferSize)
        val pcmIn= FileInputStream(pcmFilePath)
        val wavOut = FileOutputStream(wavFilePath)
        pcmLength = pcmIn.channel.size()
        //wav file header 44 bytes
        val dataLength: Long = pcmLength + 36
        //Write wav file header first
        writeHeader(wavOut, pcmLength, dataLength, sampleRate, channels, byteRate)
        //Write data again
        while (pcmIn.read(data) != -1) {
            wavOut.write(data)
        }
        Log.i(TAG, "wav File write complete")
        pcmIn.close()
        wavOut.close()
    }

    @Throws(IOException::class)
    private fun writeHeader(
        wavOut: FileOutputStream,
        pcmLength: Long,
        dataLength: Long,
        sampleRate: Int,
        channels: Int,
        byteRate: Int
    ) {
        //44 bytes of wave file header
        val header = ByteArray(44)

        /*0-11 Bytes (RIFF chunk: riff file description block)*/
        header[0] = 82 // R
        header[1] = 73 // I
        header[2] = 70 // F
        header[3] = 70 // F
        header[4] = (dataLength * 0xff).toByte() //Take a byte (lower 8 bits)
        header[5] = ((dataLength shr 8) * 0xff).toByte() //Take a byte (middle 8 bits)
        header[6] = ((dataLength shr 16) * 0xff).toByte() //Take a byte (8-bit times)
        header[7] = ((dataLength shr 24) * 0xff).toByte() //Take a byte (high 8 bits)
        header[8] = 87 // W
        header[9] = 65 // A
        header[10] = 86 // V
        header[11] = 69 // E

        /*13-35 Byte (fmt chunk: data format information block)*/
        header[12] = 102 // f
        header[13] = 109 // m
        header[14] = 116 // t
        header[15] = 32 // <space>
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate * 0xff).toByte()
        header[25] = ((sampleRate shr 8) * 0xff).toByte()
        header[26] = ((sampleRate shr 16) * 0xff).toByte()
        header[27] = ((sampleRate shr 24) * 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = 16 * 2 / 8 //
        header[33] = 0
        header[34] = 16
        header[35] = 0

        /*36 After bytes (data chunk)*/
        header[36] = 100 // d
        header[37] = 97 // a
        header[38] = 116 // t
        header[39] = 97 // a
        header[40] = (pcmLength and 0xff).toByte()
        header[41] = (pcmLength shr 8 and 0xff).toByte()
        header[42] = (pcmLength shr 16 and 0xff).toByte()
        header[43] = (pcmLength shr 24 and 0xff).toByte()

        //Write header
        wavOut.write(header, 0, 44)
    }

    fun pcmToWav2(pcmFile: File, wavFile: File) {
        val chunkSize = pcmFile.length() + 36
        val subChunk1Size = 16
        val audioFormat = 1
        val numChannels = 1
        val sampleRate = sampleRateInHz
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val subChunk2Size = pcmFile.length()

        val dataBuffer = ByteArray(minShortBufferSize)
        val pcmIn = FileInputStream(pcmFile)
        val wavOut = FileOutputStream(wavFile)

        writeHeader2(
            wavOut, chunkSize, subChunk1Size, audioFormat, numChannels, sampleRate,
            bitsPerSample, byteRate, blockAlign, subChunk2Size
        )

        //Write data again
        while (pcmIn.read(dataBuffer) != -1) {
            wavOut.write(dataBuffer)
        }
        Log.i(TAG, "wav File write complete")
        pcmIn.close()
        wavOut.close()
    }

    private fun writeHeader2(
        wavOut: FileOutputStream,
        chunkSize: Long,
        subChunk1Size: Int,
        audioFormat: Int,
        numChannels: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        byteRate: Int,
        blockAlign: Int,
        subChunk2Size: Long
    ) {
        //44 bytes of wave file header
        val header = ByteArray(44)

        /*0-11 Bytes (RIFF chunk: riff file description block)*/
        header[0] = 82 // R
        header[1] = 73 // I
        header[2] = 70 // F
        header[3] = 70 // F
        header[4] = (chunkSize and 0xff).toByte() //Take a byte (lower 8 bits)
        header[5] = ((chunkSize shr 8) and 0xff).toByte() //Take a byte (middle 8 bits)
        header[6] = ((chunkSize shr 16) and 0xff).toByte() //Take a byte (8-bit times)
        header[7] = ((chunkSize shr 24) and 0xff).toByte() //Take a byte (high 8 bits)
        header[8] = 87 // W
        header[9] = 65 // A
        header[10] = 86 // V
        header[11] = 69 // E

        /*13-35 Byte (fmt chunk: data format information block)*/
        header[12] = 102 // f
        header[13] = 109 // m
        header[14] = 116 // t
        header[15] = 32 // <space>
        header[16] = (subChunk1Size and 0xFF).toByte()
        header[17] = ((subChunk1Size shr 8) and 0xFF).toByte()
        header[18] = ((subChunk1Size shr 16) and 0xFF).toByte()
        header[19] = ((subChunk1Size shr 24) and 0xFF).toByte()
        header[20] = (audioFormat and 0xFF).toByte()
        header[21] = ((audioFormat shr 8) and 0xFF).toByte()
        header[22] = (numChannels and 0xFF).toByte()
        header[23] = ((numChannels shr 8) and 0xFF).toByte()
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (blockAlign and 0xFF).toByte()
        header[33] = ((blockAlign shr 8) and 0xFF).toByte()
        header[34] = (bitsPerSample and 0xFF).toByte()
        header[35] = ((bitsPerSample shr 8) and 0xFF).toByte()

        /*36 After bytes (data chunk)*/
        header[36] = 100 // d
        header[37] = 97 // a
        header[38] = 116 // t
        header[39] = 97 // a
        header[40] = (subChunk2Size and 0xff).toByte()
        header[41] = (subChunk2Size shr 8 and 0xff).toByte()
        header[42] = (subChunk2Size shr 16 and 0xff).toByte()
        header[43] = (subChunk2Size shr 24 and 0xff).toByte()

        //Write header
        wavOut.write(header)
    }


}