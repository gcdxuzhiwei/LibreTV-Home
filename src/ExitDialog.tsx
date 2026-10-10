import React, {useRef, useState} from 'react';
import {BackHandler, StyleSheet, Text, useTVEventHandler, View} from 'react-native';
import {Dialog, DialogHandle} from './Dialog';
import {FocusButton} from './FocusButton';

export function ExitDialog({reduced, onDismiss}: {reduced: boolean; onDismiss: () => void}) {
  const dialog = useRef<DialogHandle>(null);
  const cancel = useRef<View>(null);
  const [focused, setFocused] = useState<'cancel' | 'exit' | null>(null);
  const close = (exit = false) => dialog.current?.close(exit ? () => BackHandler.exitApp() : undefined);

  // TV Modal 的确认事件可能缺少目标节点，按当前按钮焦点承接遥控确认。
  useTVEventHandler(event => {
    if (event.eventType === 'select' && event.eventKeyAction === 1 && focused) close(focused === 'exit');
  });

  return <Dialog ref={dialog} reduced={reduced} initialFocus={cancel} onDismiss={onDismiss}>
        <Text accessibilityRole="header" style={s.title}>退出家庭影院？</Text>
        <Text style={s.description}>观看记录已保存在本机，下次打开可继续观看。</Text>
        <View style={s.actions}>
          <FocusButton label="取消" buttonRef={cancel} preferred focused={focused === 'cancel'} onPress={() => close()}
            onFocusChange={active => setFocused(current => active ? 'cancel' : current === 'cancel' ? null : current)} />
          <FocusButton label="退出" focused={focused === 'exit'} onPress={() => close(true)}
            onFocusChange={active => setFocused(current => active ? 'exit' : current === 'exit' ? null : current)} />
        </View>
  </Dialog>;
}

const s = StyleSheet.create({
  title: {color: '#F4F7FA', fontSize: 24, fontWeight: '700', letterSpacing: -0.5},
  description: {color: '#9BA3AA', fontSize: 15, lineHeight: 24, marginTop: 14},
  actions: {flexDirection: 'row', justifyContent: 'flex-end', gap: 12, marginTop: 30},
});
