import React, {forwardRef, useEffect, useImperativeHandle, useRef} from 'react';
import {Animated, Easing, Modal, StyleSheet, TVFocusGuideView, useWindowDimensions, View} from 'react-native';

export type DialogHandle = {close: (afterClose?: () => void) => void};

export const Dialog = forwardRef<DialogHandle, {
  reduced: boolean; onDismiss: () => void; initialFocus: React.RefObject<View>; children: React.ReactNode;
}>(function Dialog({reduced, onDismiss, initialFocus, children}, ref) {
  const {width} = useWindowDimensions();
  const closing = useRef(false);
  const overlay = useRef(new Animated.Value(0)).current;
  const content = useRef(new Animated.Value(0)).current;
  useEffect(() => () => {overlay.stopAnimation(); content.stopAnimation();}, [overlay, content]);

  const show = () => {
    // 原生窗口挂载后请求焦点，避免落到背景控件。
    initialFocus.current?.requestTVFocus();
    if (reduced) {overlay.setValue(1); content.setValue(1); return;}
    Animated.parallel([
      Animated.timing(overlay, {toValue: 1, duration: 200, useNativeDriver: true}),
      Animated.spring(content, {toValue: 1, damping: 24, stiffness: 300, mass: 0.8, useNativeDriver: true}),
    ]).start();
  };

  const close = (afterClose?: () => void) => {
    if (closing.current) return;
    closing.current = true;
    overlay.stopAnimation(); content.stopAnimation();
    const finish = () => {onDismiss(); afterClose?.();};
    if (reduced) {finish(); return;}
    // 离场完成后再卸载；重复确认或返回只执行一次。
    Animated.parallel([
      Animated.timing(overlay, {toValue: 0, duration: 160, useNativeDriver: true}),
      Animated.timing(content, {toValue: 0, duration: 160, easing: Easing.in(Easing.cubic), useNativeDriver: true}),
    ]).start(({finished}) => {if (finished) finish();});
  };
  useImperativeHandle(ref, () => ({close}));

  return <Modal transparent visible animationType="none" onShow={show} onRequestClose={() => close()}>
    <View style={s.stage}>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, s.shade, {opacity: overlay}]} />
      <Animated.View style={[s.card, {width: Math.min(440, width - 48), opacity: content,
        transform: [{scale: content.interpolate({inputRange: [0, 1], outputRange: [0.94, 1]})},
          {translateY: content.interpolate({inputRange: [0, 1], outputRange: [12, 0]})}]}]}>
        <TVFocusGuideView autoFocus trapFocusLeft trapFocusRight trapFocusUp trapFocusDown style={s.body}>
          {children}
        </TVFocusGuideView>
      </Animated.View>
    </View>
  </Modal>;
});

const s = StyleSheet.create({
  stage: {flex: 1, alignItems: 'center', justifyContent: 'center'},
  shade: {backgroundColor: '#000000B8'},
  card: {maxHeight: '80%', padding: 26, borderRadius: 16, backgroundColor: '#15191C', borderWidth: 1, borderColor: '#FFFFFF14', elevation: 24},
  body: {flexShrink: 1},
});
