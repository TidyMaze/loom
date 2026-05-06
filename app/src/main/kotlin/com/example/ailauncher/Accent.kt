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
    greeting = 0xFFFFFFFF.toInt(),  // pure white
    barStart = 0x55FFFFFF.toInt(),  // ~33% alpha — soft white at icon side
    barEnd   = 0x14FFFFFF.toInt(),  // ~8%  alpha — fades to near-invisible
    haloCore = 0x66FFFFFF.toInt(),  // ~40% alpha — gentle highlight
    haloMid  = 0x26FFFFFF.toInt(),  // ~15% alpha
    haloEdge = 0x00FFFFFF,          // transparent
    ambient  = 0x14FFFFFF
)
