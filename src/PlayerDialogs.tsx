import React, {useEffect, useRef, useState} from 'react';
import {AccessibilityInfo, DeviceEventEmitter, NativeModules, ScrollView, StyleSheet, Text, useTVEventHandler, View} from 'react-native';
import {Dialog, DialogHandle} from './Dialog';
import {FocusButton} from './FocusButton';

type PlayerDialog = {id: number; kind: 'episodes' | 'lines' | 'error'; title: string; message?: string; options?: string[]; selected?: number};
type Action = {name: string; index: number};
const native = NativeModules.LibreTV;

function PlayerDialogContent({data, reduced}: {data: PlayerDialog; reduced: boolean}) {
  const dialog = useRef<DialogHandle>(null);
  const first = useRef<View>(null);
  const action = useRef<Action>({name: 'close', index: -1});
  const closing = useRef(false);
  const [focused, setFocused] = useState<number | null>(null);
  const options = data.options || [];
  const buttons = data.kind === 'error'
    ? [{label: '关闭', name: 'close'}, {label: '重试', name: 'retry'}, {label: '切换线路', name: 'lines'}]
    : [{label: '关闭', name: 'close'}];
  const close = (name: string, index = -1) => {
    // 离场期间忽略后续按键，操作在动画结束后只执行一次。
    if (closing.current) return;
    action.current = {name, index}; dialog.current?.close();
  };
  const choose = (index: number) => index < options.length
    ? close('select', index) : close(buttons[index - options.length].name);
  useTVEventHandler(event => {
    if (event.eventType === 'select' && event.eventKeyAction === 1 && focused !== null) choose(focused);
  });
  const focus = (index: number, active: boolean) => setFocused(current => active ? index : current === index ? null : current);
  return <Dialog ref={dialog} reduced={reduced} initialFocus={first}
    onCloseStart={() => {closing.current = true;}}
    onDismiss={() => native.playerDialogAction(data.id, action.current.name, action.current.index)}>
    <Text accessibilityRole="header" style={s.title}>{data.title}</Text>
    {!!data.message && <ScrollView style={s.list}><Text style={s.message}>{data.message}</Text></ScrollView>}
    {!!options.length && <ScrollView style={s.list} contentContainerStyle={s.options} removeClippedSubviews={false}>
      {options.map((label, index) => <FocusButton key={index} label={`${index === data.selected ? '✓ ' : ''}${label}`}
        focused={focused === index} onFocusChange={active => focus(index, active)} onPress={() => choose(index)} />)}
    </ScrollView>}
    <View style={s.actions}>{buttons.map((button, index) => <FocusButton key={button.name} label={button.label}
      buttonRef={index === 0 ? first : undefined} preferred={index === 0} focused={focused === options.length + index}
      onFocusChange={active => focus(options.length + index, active)} onPress={() => close(button.name)} />)}</View>
  </Dialog>;
}

export function PlayerDialogs() {
  const [data, setData] = useState<PlayerDialog | null>(null);
  const [reduced, setReduced] = useState(true);
  useEffect(() => {
    let active = true;
    let received = false;
    const events = DeviceEventEmitter.addListener('LibreTVPlayerDialog', (next: PlayerDialog | null) => {
      received = true; setData(next);
    });
    native.playerDialog().then((next: PlayerDialog | null) => {if (active && !received) setData(next);}).catch(() => {});
    AccessibilityInfo.isReduceMotionEnabled().then(value => {if (active) setReduced(value);}).catch(() => {});
    const motion = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => {active = false; events.remove(); motion.remove();};
  }, []);
  return <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
    {data && <PlayerDialogContent key={data.id} data={data} reduced={reduced} />}
  </View>;
}

const s = StyleSheet.create({
  title: {color: '#F4F7FA', fontSize: 24, fontWeight: '700', marginBottom: 18},
  list: {flexGrow: 0, flexShrink: 1},
  options: {gap: 4, padding: 2},
  message: {color: '#9BA3AA', fontSize: 15, lineHeight: 24},
  actions: {flexDirection: 'row', justifyContent: 'flex-end', flexWrap: 'wrap', gap: 12, marginTop: 18},
});
