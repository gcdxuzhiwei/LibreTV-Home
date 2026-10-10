import React, {useRef, useState} from 'react';
import {ScrollView, StyleSheet, Text, useTVEventHandler, View} from 'react-native';
import {Dialog, DialogHandle} from './Dialog';
import {FocusButton} from './FocusButton';

type Option = {id: string; name: string};

export function SelectionDialog({title, options, reduced, onSelect, onDismiss}: {
  title: string; options: Option[]; reduced: boolean; onSelect: (option: Option) => void; onDismiss: () => void;
}) {
  const dialog = useRef<DialogHandle>(null);
  const first = useRef<View>(null);
  // 索引同时覆盖选项和最后的关闭按钮，不依赖每个按钮的失焦事件。
  const [focused, setFocused] = useState<number | null>(null);
  const changeFocus = (index: number, active: boolean) => setFocused(current => active ? index : current === index ? null : current);
  const select = (index: number) => {
    const option = options[index];
    dialog.current?.close(option ? () => onSelect(option) : undefined);
  };
  // TV Modal 的确认事件可能缺少目标节点，使用当前焦点对应的选项。
  useTVEventHandler(event => {
    if (event.eventType === 'select' && event.eventKeyAction === 1 && focused !== null) select(focused);
  });

  return <Dialog ref={dialog} reduced={reduced} initialFocus={first} onDismiss={onDismiss}>
    <Text accessibilityRole="header" style={s.title}>{title}</Text>
    <ScrollView style={s.list} contentContainerStyle={s.options} removeClippedSubviews={false}>
      {options.map((option, index) => <FocusButton key={option.id} label={option.name}
        buttonRef={index === 0 ? first : undefined} preferred={index === 0} focused={focused === index}
        onFocusChange={active => changeFocus(index, active)} onPress={() => select(index)} />)}
      {!options.length && <Text style={s.empty}>没有可用选项，请在影视源管理中启用来源。</Text>}
    </ScrollView>
    <View style={s.actions}><FocusButton label="关闭" buttonRef={!options.length ? first : undefined}
      preferred={!options.length} focused={focused === options.length}
      onFocusChange={active => changeFocus(options.length, active)} onPress={() => select(options.length)} /></View>
  </Dialog>;
}

const s = StyleSheet.create({
  title: {color: '#F4F7FA', fontSize: 24, fontWeight: '700', letterSpacing: -0.5, marginBottom: 18},
  list: {flexGrow: 0, flexShrink: 1},
  options: {gap: 4, padding: 2},
  empty: {color: '#9BA3AA', fontSize: 15, lineHeight: 24},
  actions: {flexDirection: 'row', justifyContent: 'flex-end', marginTop: 18},
});
