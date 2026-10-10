import React, {useState} from 'react';
import {Pressable, StyleSheet, Text, View} from 'react-native';

export function FocusButton({label, onPress, primary = false, preferred = false, disabled = false, buttonRef, nextFocusLeft, focused: controlledFocus, onFocusChange}: {
  label: string; onPress: () => void; primary?: boolean; preferred?: boolean; disabled?: boolean;
  buttonRef?: React.Ref<View>; nextFocusLeft?: number; focused?: boolean; onFocusChange?: (focused: boolean) => void;
}) {
  const [localFocus, setFocused] = useState(false);
  // 同组按钮可由父级统一焦点，避免 TV Modal 遗漏失焦事件时保留多个高亮。
  const focused = controlledFocus ?? localFocus;
  const changeFocus = (active: boolean) => {setFocused(active); onFocusChange?.(active);};
  return <Pressable ref={buttonRef} nextFocusLeft={nextFocusLeft} accessibilityRole="button" accessibilityLabel={label} disabled={disabled}
    hasTVPreferredFocus={preferred} onFocus={() => changeFocus(true)} onBlur={() => changeFocus(false)}
    onPress={onPress} style={[s.button, primary && s.primary, focused && s.focus, disabled && s.disabled]}>
    <Text numberOfLines={1} style={[s.text, (primary || focused) && s.darkText]}>{label}</Text>
  </Pressable>;
}

const s = StyleSheet.create({
  button: {minHeight: 40, paddingHorizontal: 16, paddingVertical: 10, borderRadius: 10, borderWidth: 2,
    borderColor: 'transparent', backgroundColor: '#182230', marginVertical: 3},
  primary: {backgroundColor: '#DCF763'},
  focus: {borderColor: '#FFFFFF', backgroundColor: '#DCF763', elevation: 5},
  text: {color: '#F4F7FA', fontSize: 14, fontWeight: '700', textAlign: 'center'},
  darkText: {color: '#0B1018'},
  disabled: {opacity: 0.45},
});
