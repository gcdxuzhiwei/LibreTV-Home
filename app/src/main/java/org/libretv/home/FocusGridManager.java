package org.libretv.home;

import android.view.View;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.facebook.react.uimanager.util.ReactFindViewUtil;
import com.facebook.react.views.view.ReactViewGroup;
import com.facebook.react.views.view.ReactViewManager;
import java.util.Map;

/** 网格按数据顺序导航，原生焦点移动不等待 JS 或封面加载。 */
public final class FocusGridManager extends ReactViewManager {
    @Override public String getName() { return "FocusGridView"; }
    @Override public ReactViewGroup createViewInstance(ThemedReactContext context) { return new Grid(context); }
    @ReactProp(name = "columns", defaultInt = 1)
    public void setColumns(ReactViewGroup view, int value) { ((Grid) view).columns = Math.max(1, value); }
    @ReactProp(name = "itemCount")
    public void setItemCount(ReactViewGroup view, int value) { ((Grid) view).count = value; }
    @Override public Map<String, Object> getExportedCustomDirectEventTypeConstants() {
        Map<String, Object> events = super.getExportedCustomDirectEventTypeConstants();
        Map<String, Object> result = MapBuilder.newHashMap();
        if (events != null) result.putAll(events);
        result.put("topFocusRequest", MapBuilder.of("registrationName", "onFocusRequest"));
        return result;
    }

    static int targetIndex(int index, int columns, int count, int direction) {
        if (direction == View.FOCUS_LEFT) return Math.max(0, index - 1);
        if (direction == View.FOCUS_RIGHT) return Math.min(count - 1, index + 1);
        if (direction == View.FOCUS_UP) return index < columns ? -1 : index - columns;
        if (direction == View.FOCUS_DOWN) {
            int nextRow = (index / columns + 1) * columns;
            return nextRow >= count ? -1 : Math.min(count - 1, index + columns);
        }
        return -1;
    }

    private static final class Grid extends ReactViewGroup {
        int columns = 1;
        int count;
        Runnable pending;
        Grid(ThemedReactContext context) { super(context); }

        @Override public View focusSearch(View focused, int direction) {
            Object tag = focused.getTag(com.facebook.react.R.id.view_tag_native_id);
            if (!(tag instanceof String) || !((String) tag).startsWith("grid-item-")) {
                return super.focusSearch(focused, direction);
            }
            int index;
            try { index = Integer.parseInt(((String) tag).substring(10)); }
            catch (NumberFormatException error) { return super.focusSearch(focused, direction); }
            int target = targetIndex(index, columns, count, direction);
            if (pending != null) { removeCallbacks(pending); pending = null; }
            if (target < 0) {
                // 首行上移交给页面；末行下移只允许进入加载更多按钮。
                if (direction == View.FOCUS_DOWN) {
                    View footer = ReactFindViewUtil.findView(this, "grid-footer");
                    return footer != null && footer.isFocusable() && footer.isEnabled() ? footer : focused;
                }
                return super.focusSearch(focused, direction);
            }
            if (target == index) return focused;
            View next = ReactFindViewUtil.findView(this, "grid-item-" + target);
            if (next != null && next.isShown() && next.getWidth() > 0) return next;
            // 邻行尚未被 FlatList 挂载：保留当前焦点，滚动后直接聚焦目标。
            WritableMap event = Arguments.createMap();
            event.putInt("index", target);
            ((ThemedReactContext) getContext()).getJSModule(RCTEventEmitter.class)
                .receiveEvent(getId(), "topFocusRequest", event);
            pending = new Runnable() {
                int attempts;
                @Override public void run() {
                    if (!isAttachedToWindow() || findFocus() != focused || ++attempts > 60) { pending = null; return; }
                    View mounted = ReactFindViewUtil.findView(Grid.this, "grid-item-" + target);
                    if (mounted != null && mounted.isShown() && mounted.getWidth() > 0) {
                        pending = null;
                        mounted.requestFocus();
                    } else postDelayed(this, 16);
                }
            };
            postDelayed(pending, 16);
            return focused;
        }
        @Override protected void onDetachedFromWindow() {
            if (pending != null) { removeCallbacks(pending); pending = null; }
            super.onDetachedFromWindow();
        }
    }
}
