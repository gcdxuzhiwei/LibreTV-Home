import React, {useRef, useState} from 'react';
import {ScrollView, StyleSheet, Text, useTVEventHandler, View} from 'react-native';
import {Dialog, DialogHandle} from './Dialog';
import {FocusButton} from './FocusButton';

export type DialogMessage = {title: string; message?: string; confirm?: string; onConfirm?: () => void};

export function MessageDialog({title, message, confirm, onConfirm, reduced, onDismiss}: DialogMessage & {
  reduced: boolean; onDismiss: () => void;
}) {
  const dialog = useRef<DialogHandle>(null);
  const first = useRef<View>(null);
  const [focused, setFocused] = useState<number | null>(null);
  const close = (accept: boolean) => dialog.current?.close(accept ? onConfirm : undefined);
  useTVEventHandler(event => {
    if (event.eventType === 'select' && event.eventKeyAction === 1 && focused !== null) close(!confirm || focused === 1);
  });
  const focus = (index: number, active: boolean) => setFocused(current => active ? index : current === index ? null : current);
  return <Dialog ref={dialog} reduced={reduced} initialFocus={first} onDismiss={onDismiss}>
    <Text accessibilityRole="header" style={s.title}>{title}</Text>
    {!!message && <ScrollView style={s.content}><Text style={s.description}>{message}</Text></ScrollView>}
    <View style={s.actions}>
      <FocusButton label={confirm ? '取消' : '确定'} buttonRef={first} preferred focused={focused === 0}
        onFocusChange={active => focus(0, active)} onPress={() => close(!confirm)} />
      {!!confirm && <FocusButton label={confirm} focused={focused === 1}
        onFocusChange={active => focus(1, active)} onPress={() => close(true)} />}
    </View>
  </Dialog>;
}

const s = StyleSheet.create({
  title: {color: '#F4F7FA', fontSize: 24, fontWeight: '700'},
  content: {flexGrow: 0, flexShrink: 1, marginTop: 14},
  description: {color: '#9BA3AA', fontSize: 15, lineHeight: 24},
  actions: {flexDirection: 'row', justifyContent: 'flex-end', gap: 12, marginTop: 24},
});
