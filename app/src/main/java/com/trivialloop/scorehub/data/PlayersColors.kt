package com.trivialloop.scorehub.utils

import android.graphics.Color
import kotlin.random.Random

object PlayerColors {
    private val availableColors = listOf(
        Color.parseColor("#E91E63"), // Pink
        Color.parseColor("#9C27B0"), // Purple
        Color.parseColor("#3F51B5"), // Indigo
        Color.parseColor("#2196F3"), // Blue
        Color.parseColor("#00BCD4"), // Cyan
        Color.parseColor("#009688"), // Teal
        Color.parseColor("#4CAF50"), // Green
        Color.parseColor("#8BC34A"), // Light Green
        Color.parseColor("#CDDC39"), // Lime
        Color.parseColor("#FFEB3B"), // Yellow
        Color.parseColor("#FFC107"), // Amber
        Color.parseColor("#FF9800"), // Orange
        Color.parseColor("#FF5722"), // Deep Orange
        Color.parseColor("#795548"), // Brown
        Color.parseColor("#607D8B")  // Blue Grey
    )
    
    private var colorIndex = 0
    
    fun getNextColor(): Int {
        val color = availableColors[colorIndex % availableColors.size]
        colorIndex++
        return color
    }
    
    fun getRandomColor(): Int {
        return availableColors[Random.nextInt(availableColors.size)]
    }
    
    fun getAvailableColors(): List<Int> {
        return availableColors
    }
    
    fun resetColorIndex() {
        colorIndex = 0
    }
}
