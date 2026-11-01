package com.breakingfree.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A diagonal lattice: a diamond woven from two families of crossing lines. */
val LatticeIcon: ImageVector by lazy {
    ImageVector.Builder("Lattice", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 2f); lineTo(22f, 12f); lineTo(12f, 22f); lineTo(2f, 12f); close()
        }
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.1f, strokeLineCap = StrokeCap.Round) {
            // lines parallel to the upper-left edge
            moveTo(4.5f, 14.5f); lineTo(14.5f, 4.5f)
            moveTo(7f, 17f); lineTo(17f, 7f)
            moveTo(9.5f, 19.5f); lineTo(19.5f, 9.5f)
            // lines parallel to the upper-right edge
            moveTo(9.5f, 4.5f); lineTo(19.5f, 14.5f)
            moveTo(7f, 7f); lineTo(17f, 17f)
            moveTo(4.5f, 9.5f); lineTo(14.5f, 19.5f)
        }
    }.build()
}
