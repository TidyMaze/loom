package com.yrolland.loom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    @Test
    fun `empty query matches everything`() {
        assertTrue(FuzzyMatcher.matches("", "Gmail"))
        assertTrue(FuzzyMatcher.matches("   ", "Gmail"))
    }

    @Test
    fun `exact match case insensitive`() {
        assertTrue(FuzzyMatcher.matches("gmail", "Gmail"))
        assertTrue(FuzzyMatcher.matches("GMAIL", "Gmail"))
        assertTrue(FuzzyMatcher.matches("Gmail", "Gmail"))
    }

    @Test
    fun `subsequence matching characters in order`() {
        assertTrue(FuzzyMatcher.matches("gm", "Gmail"))
        assertTrue(FuzzyMatcher.matches("gml", "Gmail"))
        assertTrue(FuzzyMatcher.matches("gma", "Gmail"))
        assertTrue(FuzzyMatcher.matches("map", "Google Maps"))
        assertTrue(FuzzyMatcher.matches("gmap", "Google Maps"))
    }

    @Test
    fun `characters out of order fails`() {
        assertFalse(FuzzyMatcher.matches("mg", "Gmail"))
        assertFalse(FuzzyMatcher.matches("lgm", "Gmail"))
    }

    @Test
    fun `completely non matching query fails`() {
        assertFalse(FuzzyMatcher.matches("xyz", "Gmail"))
    }

    @Test
    fun `empty label matches only empty query`() {
        assertTrue(FuzzyMatcher.matches("", ""))
        assertFalse(FuzzyMatcher.matches("g", ""))
    }

    @Test
    fun `ignores accents and diacritics`() {
        assertTrue(FuzzyMatcher.matches("meteo", "Météo"))
        assertTrue(FuzzyMatcher.matches("Météo", "meteo"))
        assertTrue(FuzzyMatcher.matches("mto", "Météo"))
    }
}
