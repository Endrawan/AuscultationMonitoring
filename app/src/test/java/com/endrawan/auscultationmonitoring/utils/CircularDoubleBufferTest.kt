package com.endrawan.auscultationmonitoring.utils

import org.junit.Assert.*

import org.junit.Test

class CircularDoubleBufferTest {

    @Test
    fun getBuffer() {
        val example = CircularDoubleBuffer(3)
        val actualResult = listOf(1.0, 2.0, 3.0)
        example.add(1.0)
        example.add(2.0)
        example.add(3.0)
        assertEquals(example.buffer, actualResult)
        val actualResult2 = listOf(3.0, 4.0, 5.0)
        example.add(4.0)
        example.add(5.0)
        assertEquals(example.buffer, actualResult2)
    }

    @Test
    fun add() {
        val example = CircularDoubleBuffer(3)
        val actualResult = listOf(1.0, 2.0, 3.0)
        example.add(1.0)
        example.add(2.0)
        example.add(3.0)
        assertEquals(example.buffer, actualResult)
        val actualResult2 = listOf(3.0, 4.0, 5.0)
        example.add(4.0)
        example.add(5.0)
        assertEquals(example.buffer, actualResult2)
    }

    @Test
    fun getRecent() {
        val example = CircularDoubleBuffer(3)
        val actualResult = 3f
        example.add(1.0)
        example.add(2.0)
        example.add(3.0)
        assertEquals(example.getRecent().toFloat(), actualResult)
        val actualResult2 = 5f
        example.add(4.0)
        example.add(5.0)
        assertEquals(example.getRecent().toFloat(), actualResult2)
    }

    @Test
    fun getCapacity() {
        val example = CircularDoubleBuffer(3)
        assertEquals(example.capacity, 3)
    }
}