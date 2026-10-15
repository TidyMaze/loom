package com.yrolland.loom

enum class DpadDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

object DpadNavigation {
    private const val KEYCODE_DPAD_UP = 19
    private const val KEYCODE_DPAD_DOWN = 20
    private const val KEYCODE_DPAD_LEFT = 21
    private const val KEYCODE_DPAD_RIGHT = 22
    private const val KEYCODE_DPAD_CENTER = 23
    private const val KEYCODE_ENTER = 66
    private const val KEYCODE_NUMPAD_ENTER = 160

    fun isDpadKey(keyCode: Int): Boolean {
        return keyCode == KEYCODE_DPAD_UP ||
            keyCode == KEYCODE_DPAD_DOWN ||
            keyCode == KEYCODE_DPAD_LEFT ||
            keyCode == KEYCODE_DPAD_RIGHT ||
            keyCode == KEYCODE_DPAD_CENTER ||
            keyCode == KEYCODE_ENTER ||
            keyCode == KEYCODE_NUMPAD_ENTER
    }

    fun isPrintableKey(unicodeChar: Int): Boolean {
        return unicodeChar in 32..126 || unicodeChar > 127
    }

    fun calculateNextPosition(current: Int, direction: DpadDirection, cols: Int, total: Int): Int {
        if (total <= 0) return -1
        if (current !in 0 until total) return 0

        return when (direction) {
            DpadDirection.UP -> {
                val next = current - cols
                if (next < 0) -1 else next
            }
            DpadDirection.DOWN -> {
                val next = current + cols
                if (next >= total) current else next
            }
            DpadDirection.LEFT -> {
                if (current % cols == 0) current else current - 1
            }
            DpadDirection.RIGHT -> {
                if (current % cols == cols - 1 || current + 1 >= total) current else current + 1
            }
        }
    }
}
