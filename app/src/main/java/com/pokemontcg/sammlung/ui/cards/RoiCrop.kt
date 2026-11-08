package com.pokemontcg.sammlung.ui.cards

/**
 * Pixel rectangle inside a (already rotation-corrected) camera analysis bitmap.
 */
data class CropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Maps the centered on-screen scan frame to the crop rectangle inside the analysis bitmap.
 *
 * The preview scales the analysis bitmap with FILL_CENTER (scale to cover the view, then
 * center), exactly like CameraX's PreviewView does. Inverting that mapping turns the
 * on-screen frame into the bitmap region that OCR should read in ROI mode. Invalid inputs
 * (zero sizes) fall back to the full frame, so ROI mode can never produce an empty crop.
 */
fun computeRoiCropRect(
    frameWidthPx: Int,
    frameHeightPx: Int,
    viewWidthPx: Int,
    viewHeightPx: Int,
    bitmapWidth: Int,
    bitmapHeight: Int
): CropRect {
    val full = CropRect(0, 0, bitmapWidth, bitmapHeight)
    if (viewWidthPx <= 0 || viewHeightPx <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0 ||
        frameWidthPx <= 0 || frameHeightPx <= 0
    ) {
        return full
    }
    val scale = maxOf(viewWidthPx.toFloat() / bitmapWidth, viewHeightPx.toFloat() / bitmapHeight)
    // The overlay frame is centered in the view (see CardScannerScreen).
    val frameLeft = (viewWidthPx - frameWidthPx) / 2f
    val frameTop = (viewHeightPx - frameHeightPx) / 2f
    val left = (frameLeft / scale).toInt().coerceIn(0, bitmapWidth)
    val top = (frameTop / scale).toInt().coerceIn(0, bitmapHeight)
    val right = ((frameLeft + frameWidthPx) / scale).toInt().coerceIn(0, bitmapWidth)
    val bottom = ((frameTop + frameHeightPx) / scale).toInt().coerceIn(0, bitmapHeight)
    if (right - left <= 0 || bottom - top <= 0) {
        return full
    }
    return CropRect(left, top, right, bottom)
}
