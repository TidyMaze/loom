package com.example.ailauncher

data class Accent(
    val greeting: Int,
    val barStart: Int,
    val barEnd: Int,
    val haloCore: Int,
    val haloMid: Int,
    val haloEdge: Int,
    val ambient: Int
)

// Single gold palette. Greeting text still shifts by hour ("Good morning." etc),
// but the accent color stays gold throughout the day.
fun accentForNow(): Accent = Accent(
    greeting = 0xFFFFD060.toInt(),  // saturated warm gold
    barStart = 0xCCFFB300.toInt(),  // ~80% alpha — bright warm gold at icon side
    barEnd   = 0x66996300.toInt(),  // ~40% alpha — dark gold tail
    haloCore = 0xE6FFE680.toInt(),  // ~90% alpha — cream-gold metallic highlight
    haloMid  = 0x99FFC107.toInt(),  // ~60% alpha — pure gold
    haloEdge = 0x00B8860B,          // transparent dark goldenrod
    ambient  = 0x33FFB300
)
