package com.endrawan.auscultationmonitoring.utils

import org.junit.Assert.*

import org.junit.Test

class CircularIntBufferTest {

    @Test
    fun getBuffer() {
        val example = CircularIntBuffer(3)
        assertArrayEquals(example.buffer, intArrayOf(0, 0, 0))
    }

    @Test
    fun getCapacity() {
        val example = CircularIntBuffer(5)
        assertEquals(example.capacity, 5)
    }

    @Test
    fun testAdd() {
        val example = CircularIntBuffer(3)
        example.add(1)
        assertArrayEquals(example.buffer, intArrayOf(1, 0, 0))
        example.add(2)
        assertArrayEquals(example.buffer, intArrayOf(1, 2, 0))
        example.add(3)
        assertArrayEquals(example.buffer, intArrayOf(1, 2, 3))
        example.add(4)
        assertArrayEquals(example.buffer, intArrayOf(4, 2, 3))
    }

    @Test
    fun testGetSequence() {
        val example = CircularIntBuffer(3)
        example.add(1)
        example.add(2)
        example.add(3)
        example.add(4)
        assertArrayEquals(example.getSequence(), intArrayOf(2, 3, 4))
    }

    @Test
    fun testGetRecent() {
        val example = CircularIntBuffer(3)
        example.add(1)
        assertEquals(example.getRecent(), 1)
        example.add(2)
        assertEquals(example.getRecent(), 2)
        example.add(3)
        assertEquals(example.getRecent(), 3)
        example.add(4)
        assertEquals(example.getRecent(), 4)
    }
}