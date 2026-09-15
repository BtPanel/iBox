package com.imedia.box.ui.overlay;

final class FloatingWindowGeometry {

    static final int CORNER_NONE = 0;
    static final int CORNER_TOP_LEFT = 1;
    static final int CORNER_TOP_RIGHT = 2;
    static final int CORNER_BOTTOM_LEFT = 3;
    static final int CORNER_BOTTOM_RIGHT = 4;

    private static final float DEFAULT_ASPECT_RATIO = 16f / 9f;

    private FloatingWindowGeometry() {
    }

    static int scaleWidth(int width, float scaleFactor, int minWidth, int maxWidth) {
        return Math.clamp(Math.round(width * scaleFactor), minWidth, maxWidth);
    }

    static int heightForWidth(int width, float aspectRatio) {
        return Math.max(1, Math.round(width / validAspectRatio(aspectRatio)));
    }

    static float sourceAspectRatio(int width, int height, float pixelWidthHeightRatio) {
        if (width <= 0 || height <= 0 || !Float.isFinite(pixelWidthHeightRatio)
                || pixelWidthHeightRatio <= 0f) return DEFAULT_ASPECT_RATIO;
        return validAspectRatio(width * pixelWidthHeightRatio / height);
    }

    static float updatedAspectRatio(float currentAspectRatio, int width, int height,
                                    float pixelWidthHeightRatio) {
        if (width <= 0 || height <= 0 || !Float.isFinite(pixelWidthHeightRatio)
                || pixelWidthHeightRatio <= 0f) return validAspectRatio(currentAspectRatio);
        return sourceAspectRatio(width, height, pixelWidthHeightRatio);
    }

    static int maxWidth(int screenWidth, int screenHeight, int margin, float aspectRatio) {
        int availableWidth = Math.max(1, screenWidth - margin * 2);
        int availableHeight = Math.max(1, screenHeight - margin * 2);
        int widthFromHeight = Math.max(1,
                (int) Math.floor(availableHeight * validAspectRatio(aspectRatio)));
        return Math.min(availableWidth, widthFromHeight);
    }

    static int centeredPosition(int position, int oldSize, int newSize, int boundSize) {
        int centered = position + (oldSize - newSize) / 2;
        return Math.clamp(centered, 0, Math.max(0, boundSize - newSize));
    }

    static int cornerAt(float x, float y, int width, int height, int touchSize) {
        boolean left = x >= 0 && x <= touchSize;
        boolean right = x <= width && x >= width - touchSize;
        boolean top = y >= 0 && y <= touchSize;
        boolean bottom = y <= height && y >= height - touchSize;
        if (left && top) return CORNER_TOP_LEFT;
        if (right && top) return CORNER_TOP_RIGHT;
        if (left && bottom) return CORNER_BOTTOM_LEFT;
        if (right && bottom) return CORNER_BOTTOM_RIGHT;
        return CORNER_NONE;
    }

    static float projectWidthDelta(float dx, float dy, float aspectRatio,
                                   boolean right, boolean bottom) {
        float ratio = validAspectRatio(aspectRatio);
        float horizontal = right ? dx : -dx;
        float verticalDirection = bottom ? 1f : -1f;
        float inverseRatio = 1f / ratio;
        return (horizontal + dy * verticalDirection * inverseRatio)
                / (1f + inverseRatio * inverseRatio);
    }

    static int anchoredPosition(int position, int oldSize, int newSize,
                                boolean resizeFromStart, int boundSize) {
        int anchored = resizeFromStart ? position + oldSize - newSize : position;
        return Math.clamp(anchored, 0, Math.max(0, boundSize - newSize));
    }

    private static float validAspectRatio(float aspectRatio) {
        return Float.isFinite(aspectRatio) && aspectRatio > 0f
                ? aspectRatio : DEFAULT_ASPECT_RATIO;
    }
}
