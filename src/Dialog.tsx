import React, {forwardRef, useEffect, useImperativeHandle, useRef} from 'react';
import {Animated, Easing, Modal, requireNativeComponent, StyleSheet, TVFocusGuideView, useWindowDimensions, View, ViewProps} from 'react-native';

const BlurView = requireNativeComponent<ViewProps & {blurRadius: number; backdrop?: boolean}>('DialogBlurView');
const AnimatedBlurView = Animated.createAnimatedComponent(BlurView);
// 沿用 Motion 示例的曲线，时长减半：进场延迟 100ms + 250ms，离场 150ms。
const enterEase = Easing.bezier(0.17, 0.67, 0.51, 1);
const exitEase = Easing.bezier(0.67, 0.17, 0.62, 0.64);
const opacityEase = Easing.bezier(0, 0, 0.58, 1);
// perspective(500) translateZ(-100→0) 等效为透视距离 600→500 与非线性缩放。
const depthSteps = Array.from({length: 101}, (_, index) => index / 100);
const depthScales = depthSteps.map(progress => 500 / (600 - 100 * progress));

export type DialogHandle = {close: (afterClose?: () => void) => void};

export const Dialog = forwardRef<DialogHandle, {
  reduced: boolean; onDismiss: () => void; onCloseStart?: () => void; initialFocus: React.RefObject<View>; children: React.ReactNode;
}>(function Dialog({reduced, onDismiss, onCloseStart, initialFocus, children}, ref) {
  const {width} = useWindowDimensions();
  const closing = useRef(false);
  const animation = useRef<Animated.CompositeAnimation | null>(null);
  const overlay = useRef(new Animated.Value(0)).current;
  const content = useRef(new Animated.Value(0)).current;
  const opacity = useRef(new Animated.Value(0)).current;
  const blur = useRef(new Animated.Value(10)).current;
  useEffect(() => () => {
    animation.current?.stop();
    overlay.stopAnimation(); content.stopAnimation(); opacity.stopAnimation(); blur.stopAnimation();
  }, [overlay, content, opacity, blur]);

  const show = () => {
    if (closing.current) return;
    // 原生窗口挂载后请求焦点，避免落到背景控件。
    initialFocus.current?.requestTVFocus();
    if (reduced) {overlay.setValue(1); content.setValue(1); opacity.setValue(1); blur.setValue(0); return;}
    animation.current = Animated.parallel([
      Animated.timing(overlay, {toValue: 1, duration: 150, easing: opacityEase, useNativeDriver: true}),
      Animated.timing(content, {toValue: 1, delay: 100, duration: 250, easing: enterEase, useNativeDriver: true}),
      Animated.timing(opacity, {toValue: 1, delay: 100, duration: 250, easing: opacityEase, useNativeDriver: true}),
      Animated.timing(blur, {toValue: 0, delay: 100, duration: 250, easing: enterEase, useNativeDriver: false}),
    ]);
    animation.current.start();
  };

  const close = (afterClose?: () => void) => {
    if (closing.current) return;
    closing.current = true;
    onCloseStart?.();
    // 取消完整序列，包含还未开始的 100ms 延迟，防止快速返回后再次进场。
    animation.current?.stop();
    overlay.stopAnimation(); content.stopAnimation(); opacity.stopAnimation(); blur.stopAnimation();
    const finish = () => {onDismiss(); afterClose?.();};
    if (reduced) {finish(); return;}
    // 离场完成后再卸载；重复确认或返回只执行一次。
    animation.current = Animated.parallel([
      Animated.timing(overlay, {toValue: 0, duration: 150, easing: opacityEase, useNativeDriver: true}),
      Animated.timing(content, {toValue: 0, duration: 150, easing: exitEase, useNativeDriver: true}),
      Animated.timing(opacity, {toValue: 0, duration: 150, easing: exitEase, useNativeDriver: true}),
      Animated.timing(blur, {toValue: 10, duration: 150, easing: exitEase, useNativeDriver: false}),
    ]);
    animation.current.start(({finished}) => {if (finished) finish();});
  };
  useImperativeHandle(ref, () => ({close}));

  return <Modal transparent visible animationType="none" onShow={show} onRequestClose={() => close()}>
    <View style={s.stage}>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, {opacity: overlay}]}>
        <BlurView backdrop blurRadius={3} style={StyleSheet.absoluteFill} />
        <View style={[StyleSheet.absoluteFill, s.shade]} />
      </Animated.View>
      <Animated.View style={{width: Math.min(440, width - 48), maxHeight: '80%', opacity,
        transform: [{perspective: content.interpolate({inputRange: [0, 1], outputRange: [600, 500]})},
          {scale: content.interpolate({inputRange: depthSteps, outputRange: depthScales})},
          {rotateX: content.interpolate({inputRange: [0, 1], outputRange: ['5deg', '0deg']})},
          {rotateY: content.interpolate({inputRange: [0, 1], outputRange: ['25deg', '0deg']})}]}}>
        <AnimatedBlurView blurRadius={blur} style={s.card}>
          <TVFocusGuideView autoFocus trapFocusLeft trapFocusRight trapFocusUp trapFocusDown style={s.body}>
            {children}
          </TVFocusGuideView>
        </AnimatedBlurView>
      </Animated.View>
    </View>
  </Modal>;
});

const s = StyleSheet.create({
  stage: {flex: 1, alignItems: 'center', justifyContent: 'center'},
  shade: {backgroundColor: '#00000080'},
  card: {flexShrink: 1, padding: 26, borderRadius: 16, backgroundColor: '#15191C', borderWidth: 1, borderColor: '#FFFFFF14', elevation: 24},
  body: {flexShrink: 1},
});
