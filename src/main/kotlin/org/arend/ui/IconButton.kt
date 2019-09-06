package org.arend.ui

import com.intellij.openapi.ui.FixedSizeButton
import javax.swing.Icon
import kotlin.math.max


class IconButton(icon: Icon) : FixedSizeButton(max(icon.iconWidth, icon.iconHeight) * 2) {
    init {
        this.icon = icon
        isFocusable = true
    }
}