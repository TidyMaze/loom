package com.example.ailauncher

import java.time.LocalTime

data class Accent(
    val greeting: Int,
    val barStart: Int,
    val barEnd: Int,
    val haloCore: Int,
    val haloMid: Int,
    val haloEdge: Int,
    val ambient: Int
)

fun accentForNow(): Accent = when (LocalTime.now().hour) {
    in 5..11  -> Accent(   // morning — cool blue
        greeting = 0xFFA8C5FF.toInt(),
        barStart = 0x8CA8C5FF.toInt(),
        barEnd   = 0x4D4D7BD9.toInt(),
        haloCore = 0xA6A8C5FF.toInt(),
        haloMid  = 0x664D7BD9.toInt(),
        haloEdge = 0x002E5BB8,
        ambient  = 0x2E4D7BD9
    )
    in 12..17 -> Accent(   // afternoon — warm yellow
        greeting = 0xFFFFE0A0.toInt(),
        barStart = 0x8CFFE0A0.toInt(),
        barEnd   = 0x4DFFAE3D.toInt(),
        haloCore = 0xA6FFE0A0.toInt(),
        haloMid  = 0x66FFAE3D.toInt(),
        haloEdge = 0x00E68A00,
        ambient  = 0x2EFFAE3D
    )
    in 18..21 -> Accent(   // evening — warm orange
        greeting = 0xFFFFB088.toInt(),
        barStart = 0x8CFFB088.toInt(),
        barEnd   = 0x4DFF6B35.toInt(),
        haloCore = 0xA6FFB088.toInt(),
        haloMid  = 0x66FF8550.toInt(),
        haloEdge = 0x00FF6B35,
        ambient  = 0x2EFF6B35
    )
    else      -> Accent(   // night — purple
        greeting = 0xFFD6B0FF.toInt(),
        barStart = 0x8CD6B0FF.toInt(),
        barEnd   = 0x4D8E4DD9.toInt(),
        haloCore = 0xA6D6B0FF.toInt(),
        haloMid  = 0x668E4DD9.toInt(),
        haloEdge = 0x006E2BB8,
        ambient  = 0x2E8E4DD9
    )
}
