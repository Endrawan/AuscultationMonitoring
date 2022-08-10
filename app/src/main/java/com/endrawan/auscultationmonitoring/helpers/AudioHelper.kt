package com.endrawan.auscultationmonitoring.helpers

import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class AudioHelper(
    val channelConfig: Int,
    val sampleRateInHz: Int,
    val minShortBufferSize: Int
    ) {
    private val TAG = "AudioHelper"

    fun streamAudio(audioData: ShortArray, audioTrack: AudioTrack) {
//        val streamAudioHandler = Handler(Looper.getMainLooper())
//        val streamAudioCode = Runnable {
//            audioTrack.write(audioData, 0, minShortBufferSize)
//        }
//        streamAudioHandler.post(streamAudioCode)

        val runnable = Runnable {
            audioTrack.write(audioData, 0, minShortBufferSize)
        }
        Thread(runnable).start()
    }

    fun writeAudio(audioData: ShortArray, dataOutputStream: DataOutputStream) {
//        val writeAudioHandler = Handler(Looper.getMainLooper())
//        val writeAudioCode = Runnable {
//            for(a in audioData) {
//                val littleEndian = ByteArray(2)
//                littleEndian[0] = (a.toInt() and 0xFF).toByte()
//                littleEndian[1] = ((a.toInt() shr 8) and 0xFF).toByte()
//                dataOutputStream.write(littleEndian)
//            }
//        }
//        writeAudioHandler.post(writeAudioCode)
        val runnable = Runnable {
            for(a in audioData) {
                val littleEndian = ByteArray(2)
                littleEndian[0] = (a.toInt() and 0xFF).toByte()
                littleEndian[1] = ((a.toInt() shr 8) and 0xFF).toByte()
                dataOutputStream.write(littleEndian)
            }
        }
        Thread(runnable).start()
    }

    fun pcmToWav(pcmFile: File, wavFile: File) {
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

        writeHeader(
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

    private fun writeHeader(
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