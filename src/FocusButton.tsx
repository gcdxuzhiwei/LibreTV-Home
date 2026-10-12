import React, {useState} from 'react';
import {Pressable, StyleSheet, Text, View} from 'react-native';

export function FocusButton({label, onPress, preferred = false, disabled = false, buttonRef, nextFocusLeft, nextFocusUp, focused: controlledFocus, onFocusChange, nativeID}: {
  label: string; onPress: () => void; preferred?: boolean; disabled?: boolean;
  buttonRef?: React.Ref<View>; nextFocusLeft?: number; nextFocusUp?: number; focused?: boolean; onFocusChange?: (focused: boolean) => void;
  nativeID?: string;
}) {
  const [localFocus, setFocused] = useState(false);
  // 同组按钮可由父级统一焦点，避免 TV Modal 遗漏失焦事件时保留多个高亮。
  const focused = controlledFocus ?? localFocus;
  const changeFocus = (active: boolean) => {setFocused(active); onFocusChange?.(active);};
  return <Pressable ref={buttonRef} nativeID={nativeID} nextFocusLeft={nextFocusLeft} nextFocusUp={nextFocusUp} accessibilityRole="button" accessibilityLabel={label} disabled={disabled}
    hasTVPreferredFocus={preferred} onFocus={() => changeFocus(true)} onBlur={() => changeFocus(false)}
    onPress={onPress} style={[s.button, focused && s.focus, disabled && s.disabled]}>
    <Text numberOfLines={1} style={[s.text, focused && s.darkText]}>{label}</Text>
  </Pressable>;
}

const s = StyleSheet.create({
  button: {minHeight: 40, paddingHorizontal: 16, paddingVertical: 10, borderRadius: 10, borderWidth: 2,
    borderColor: 'transparent', backgroundColor: '#182230', marginVertical: 3},
  focus: {borderColor: '#FFFFFF', backgroundColor: '#DCF763', elevation: 5},
  text: {color: '#F4F7FA', fontSize: 14, fontWeight: '700', textAlign: 'center'},
  darkText: {color: '#0B1018'},
  disabled: {opacity: 0.45},
});
