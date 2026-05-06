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
    greeting = 0xFFFFD580.toInt(),  // warm light gold
    barStart = 0x8CFFD580.toInt(),  // ~55% alpha — bright gold at icon side
    barEnd   = 0x4DB8860B.toInt(),  // ~30% alpha — dark gold tail
    haloCore = 0xB3FFE9A8.toInt(),  // ~70% alpha — pale cream-gold core
    haloMid  = 0x66FFD700.toInt(),  // ~40% alpha — pure gold
    haloEdge = 0x00DAA520,          // transparent goldenrod
    ambient  = 0x2EB8860B
)
