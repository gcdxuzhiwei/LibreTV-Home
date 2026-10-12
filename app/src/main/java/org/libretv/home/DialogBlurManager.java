package org.libretv.home;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.view.ReactViewGroup;
import com.facebook.react.views.view.ReactViewManager;

/** 弹窗及背景模糊；缩小位图后绘制，兼容 API 23，不依赖新版 RenderEffect。 */
public final class DialogBlurManager extends ReactViewManager {
    @Override public String getName() { return "DialogBlurView"; }
    @Override public ReactViewGroup createViewInstance(ThemedReactContext context) { return new BlurView(context); }
    @ReactProp(name = "blurRadius")
    public void setBlurRadius(ReactViewGroup view, float radius) { ((BlurView) view).setRadius(radius); }
    @ReactProp(name = "backdrop", defaultBoolean = false)
    public void setBackdrop(ReactViewGroup view, boolean backdrop) {
        ((BlurView) view).backdrop = backdrop;
        view.invalidate();
    }

    private static final class BlurView extends ReactViewGroup {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect destination = new Rect();
        private float radius;
        private boolean backdrop;
        private boolean captured;
        private Bitmap bitmap;
        private int[] pixels;
        private int[] scratch;

        BlurView(ThemedReactContext context) {
            super(context);
            // 空背景 ViewGroup 默认跳过 draw；背景快照视图也必须执行自定义绘制。
            setWillNotDraw(false);
        }
        void setRadius(float value) { radius = Math.max(0, value); invalidate(); }

        @Override public void draw(Canvas canvas) {
            if ((!backdrop && radius < 0.01f) || getWidth() == 0 || getHeight() == 0) {
                super.draw(canvas);
                return;
            }
            float radiusPixels = radius * getResources().getDisplayMetrics().density;
            // 接近清晰时恢复原始分辨率，避免末帧从低分辨率快照突然跳到清晰文本。
            int sample = Math.max(1, Math.min(4, (int) Math.ceil(radiusPixels)));
            int width = Math.max(1, (getWidth() + sample - 1) / sample);
            int height = Math.max(1, (getHeight() + sample - 1) / sample);
            if (bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height) {
                releaseBitmap();
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                pixels = new int[width * height];
                scratch = new int[pixels.length];
            }
            if (!backdrop || !captured) {
                bitmap.eraseColor(0);
                Canvas buffer = new Canvas(bitmap);
                buffer.scale((float) width / getWidth(), (float) height / getHeight());
                if (backdrop) {
                    Activity activity = ((ThemedReactContext) getContext()).getCurrentActivity();
                    if (activity == null) return;
                    // Modal 位于独立窗口，主 Activity 的 decor 不包含当前弹窗。
                    View root = activity.getWindow().getDecorView();
                    if (root.getWidth() == 0 || root.getHeight() == 0) return;
                    buffer.scale((float) getWidth() / root.getWidth(), (float) getHeight() / root.getHeight());
                    root.draw(buffer);
                } else {
                    super.draw(buffer);
                }
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                float sigma = radiusPixels / sample;
                // 三次可分离盒式滤波近似高斯模糊；每帧复用像素缓冲。
                double boxWidth = Math.sqrt(4 * sigma * sigma + 1);
                int lower = (int) Math.floor(boxWidth);
                if (lower % 2 == 0) lower--;
                lower = Math.max(1, lower);
                int upper = lower + 2;
                int lowerCount = (int) Math.round((12 * sigma * sigma - 3 * lower * lower - 12 * lower - 9.0) / (-4 * lower - 4));
                for (int pass = 0; pass < 3; pass++) {
                    int boxRadius = ((pass < lowerCount ? lower : upper) - 1) / 2;
                    if (boxRadius == 0) continue;
                    boxBlur(pixels, scratch, width, height, boxRadius, true);
                    boxBlur(scratch, pixels, width, height, boxRadius, false);
                }
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
                captured = true;
            }
            destination.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(bitmap, null, destination, paint);
        }

        private static void boxBlur(int[] input, int[] output, int width, int height, int radius, boolean horizontal) {
            int length = horizontal ? width : height;
            int lines = horizontal ? height : width;
            int stride = horizontal ? 1 : width;
            int count = radius * 2 + 1;
            for (int line = 0; line < lines; line++) {
                int base = horizontal ? line * width : line;
                int a = 0, r = 0, g = 0, b = 0;
                for (int offset = -radius; offset <= radius; offset++) {
                    int color = input[base + Math.max(0, Math.min(length - 1, offset)) * stride];
                    a += color >>> 24; r += (color >>> 16) & 255; g += (color >>> 8) & 255; b += color & 255;
                }
                for (int position = 0; position < length; position++) {
                    output[base + position * stride] = ((a / count) << 24) | ((r / count) << 16) | ((g / count) << 8) | (b / count);
                    int leaving = input[base + Math.max(0, position - radius) * stride];
                    int entering = input[base + Math.min(length - 1, position + radius + 1) * stride];
                    a += (entering >>> 24) - (leaving >>> 24);
                    r += ((entering >>> 16) & 255) - ((leaving >>> 16) & 255);
                    g += ((entering >>> 8) & 255) - ((leaving >>> 8) & 255);
                    b += (entering & 255) - (leaving & 255);
                }
            }
        }

        private void releaseBitmap() {
            if (bitmap != null) bitmap.recycle();
            bitmap = null; pixels = null; scratch = null; captured = false;
        }
        @Override protected void onDetachedFromWindow() {
            releaseBitmap();
            super.onDetachedFromWindow();
        }
    }
}
