import React, {useEffect, useRef, useState} from 'react';
import {Animated, Easing, Pressable, StyleSheet, Text, View} from 'react-native';

export function FocusCheckbox({label, checked, onChange, reduced = false, disabled = false, preferred = false, checkboxRef}: {
  label: string; checked: boolean; onChange: (checked: boolean) => void; reduced?: boolean;
  disabled?: boolean; preferred?: boolean; checkboxRef?: React.Ref<View>;
}) {
  const [focused, setFocused] = useState(false);
  const progress = useRef(new Animated.Value(checked ? 1 : 0)).current;
  const scale = useRef(new Animated.Value(1)).current;
  const [pressed, setPressed] = useState(false);

  useEffect(() => {
    progress.stopAnimation();
    if (reduced) progress.setValue(checked ? 1 : 0);
    else Animated.timing(progress, {toValue: checked ? 1 : 0, duration: checked ? 220 : 160,
      easing: Easing.out(Easing.cubic), useNativeDriver: false}).start();
    return () => progress.stopAnimation();
  }, [checked, reduced, progress]);

  useEffect(() => {
    scale.stopAnimation();
    const target = pressed && !disabled ? 0.92 : focused ? 1.06 : 1;
    if (reduced) scale.setValue(1);
    else Animated.spring(scale, {toValue: target, speed: 28, bounciness: 4, useNativeDriver: true}).start();
    return () => scale.stopAnimation();
  }, [focused, pressed, disabled, reduced, scale]);

  // 两段线从左端依次伸展，模拟 Motion 的勾选路径绘制；取消时沿路径收回。
  const shortLength = Math.sqrt(32);
  const longLength = Math.sqrt(128);
  return <Pressable ref={checkboxRef} accessibilityRole="checkbox" accessibilityLabel={label}
    accessibilityState={{checked, disabled}} disabled={disabled} hasTVPreferredFocus={preferred}
    onFocus={() => setFocused(true)} onBlur={() => {setFocused(false); setPressed(false);}}
    onPressIn={() => setPressed(true)} onPressOut={() => setPressed(false)}
    onPress={() => onChange(!checked)} style={[s.control, disabled && s.disabled]}>
    <Animated.View style={[s.focusRing, focused && s.focused, {transform: [{scale}]}]}>
      <Animated.View style={[s.box, {
        backgroundColor: progress.interpolate({inputRange: [0, 1], outputRange: ['#131D2B', '#DCF763']}),
        borderColor: progress.interpolate({inputRange: [0, 1], outputRange: ['#66768D', '#DCF763']}),
      }]}>
        <View pointerEvents="none" style={s.tick}>
          <View style={[s.stroke, {left: 6 - shortLength / 2, top: 10.75, width: shortLength, transform: [{rotate: '45deg'}]}]}>
            <Animated.View style={[s.ink, {width: progress.interpolate({inputRange: [0, 0.32, 1], outputRange: [0, shortLength, shortLength], extrapolate: 'clamp'})}]} />
          </View>
          <View style={[s.stroke, {left: 12 - longLength / 2, top: 8.75, width: longLength, transform: [{rotate: '-45deg'}]}]}>
            <Animated.View style={[s.ink, {width: progress.interpolate({inputRange: [0, 0.32, 1], outputRange: [0, 0, longLength], extrapolate: 'clamp'})}]} />
          </View>
        </View>
      </Animated.View>
    </Animated.View>
    <Text numberOfLines={1} style={[s.label, focused && s.focusedLabel]}>{label}</Text>
  </Pressable>;
}

const s = StyleSheet.create({
  control: {minHeight: 44, flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 6, maxWidth: '100%'},
  focusRing: {borderWidth: 2, borderColor: 'transparent', borderRadius: 9, padding: 3},
  focused: {borderColor: '#FFFFFF'},
  box: {width: 24, height: 24, borderWidth: 2, borderRadius: 5},
  tick: {width: 20, height: 20},
  stroke: {position: 'absolute', height: 2.5},
  ink: {height: 2.5, borderRadius: 1.25, backgroundColor: '#0B1018'},
  label: {flexShrink: 1, color: '#F4F7FA', fontSize: 17},
  focusedLabel: {color: '#DCF763'},
  disabled: {opacity: 0.45},
});
