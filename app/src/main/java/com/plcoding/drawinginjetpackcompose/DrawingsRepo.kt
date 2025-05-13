package com.plcoding.drawinginjetpackcompose

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object DrawingsRepo {
    private val _drawings = MutableStateFlow<Map<String, List<PathData>>>(emptyMap())
    val drawings = _drawings.asStateFlow()

    fun addDrawing(id: String, path: List<PathData>) {
        _drawings.update {
            it.toMutableMap().apply {
                put(id, path)
                toMap()
            }
        }
    }
}