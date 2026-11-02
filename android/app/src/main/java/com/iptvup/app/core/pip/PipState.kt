package com.iptvup.app.core.pip

import kotlinx.coroutines.flow.MutableStateFlow

object PipState {
    val isInPip = MutableStateFlow(false)
}
