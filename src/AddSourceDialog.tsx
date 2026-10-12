import React, {useRef, useState} from 'react';
import {Keyboard, Pressable, StyleSheet, Text, TextInput, useTVEventHandler, View} from 'react-native';
import {Dialog, DialogHandle} from './Dialog';
import {FocusButton} from './FocusButton';
import {Source} from './api';

export function AddSourceDialog({reduced, sources, onSave, onDismiss}: {
  reduced: boolean; sources: Source[]; onSave: (source: Source) => Promise<void>; onDismiss: () => void;
}) {
  const dialog = useRef<DialogHandle>(null);
  const first = useRef<View>(null);
  const nameInput = useRef<TextInput>(null);
  const urlInput = useRef<TextInput>(null);
  const saving = useRef(false);
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [focused, setFocused] = useState<number | null>(null);
  const focus = (index: number, active: boolean) => setFocused(current => active ? index : current === index ? null : current);
  const close = () => {if (!saving.current) {Keyboard.dismiss(); dialog.current?.close();}};
  const save = async () => {
    if (saving.current) return;
    const next = {name: name.trim(), url: url.trim(), enabled: true};
    if (!next.name || !/^https?:\/\/[^\s/?#]+(?:[/?#][^\s]*)?$/i.test(next.url)) {setError('填写名称和有效的 http(s) 接口地址'); return;}
    if (sources.some(source => source.url === next.url)) {setError('该地址已存在'); return;}
    saving.current = true; setBusy(true); setError(''); Keyboard.dismiss();
    try {await onSave(next); dialog.current?.close();}
    catch (err) {setError(err instanceof Error ? err.message : String(err));}
    finally {saving.current = false; setBusy(false);}
  };
  useTVEventHandler(event => {
    if (event.eventType === 'select' && event.eventKeyAction === 1) {
      if (focused === 0) nameInput.current?.focus();
      else if (focused === 1) urlInput.current?.focus();
      else if (focused === 2) close();
      else if (focused === 3) void save();
    }
  });
  return <Dialog ref={dialog} reduced={reduced} initialFocus={first} onDismiss={onDismiss}>
    <Text accessibilityRole="header" style={s.title}>添加苹果 CMS JSON 接口</Text>
    <Pressable ref={first} hasTVPreferredFocus accessibilityRole="button" accessibilityLabel="来源名称"
      style={[s.field, focused === 0 && s.focus]} onFocus={() => focus(0, true)} onBlur={() => focus(0, false)} onPress={() => nameInput.current?.focus()}>
      <TextInput ref={nameInput} focusable={false} style={s.input} value={name} onChangeText={setName} placeholder="来源名称"
        placeholderTextColor="#96A6BB" accessibilityLabel="输入来源名称" onFocus={() => setFocused(null)}
        returnKeyType="next" onSubmitEditing={() => urlInput.current?.focus()} blurOnSubmit={false} />
    </Pressable>
    <Pressable accessibilityRole="button" accessibilityLabel="接口地址" style={[s.field, focused === 1 && s.focus]}
      onFocus={() => focus(1, true)} onBlur={() => focus(1, false)} onPress={() => urlInput.current?.focus()}>
      <TextInput ref={urlInput} focusable={false} style={s.input} value={url} onChangeText={setUrl} placeholder="http(s)://…/api.php/provide/vod/"
        placeholderTextColor="#96A6BB" accessibilityLabel="输入接口地址" autoCapitalize="none" autoCorrect={false}
        keyboardType="url" returnKeyType="done" onFocus={() => setFocused(null)} onSubmitEditing={() => Keyboard.dismiss()} />
    </Pressable>
    {!!error && <Text style={s.error}>{error}</Text>}
    <View style={s.actions}>
      <FocusButton label="取消" disabled={busy} focused={focused === 2} onFocusChange={active => focus(2, active)} onPress={close} />
      <FocusButton label={busy ? '保存中…' : '保存'} disabled={busy} focused={focused === 3} onFocusChange={active => focus(3, active)} onPress={() => void save()} />
    </View>
  </Dialog>;
}

const s = StyleSheet.create({
  title: {color: '#F4F7FA', fontSize: 22, fontWeight: '700', marginBottom: 14},
  field: {borderWidth: 2, borderColor: '#33435B', borderRadius: 10, marginTop: 10, backgroundColor: '#131D2B'},
  focus: {borderColor: '#DCF763'},
  input: {height: 44, paddingHorizontal: 12, color: '#F4F7FA', fontSize: 15},
  error: {color: '#E4C997', fontSize: 13, marginTop: 10},
  actions: {flexDirection: 'row', justifyContent: 'flex-end', gap: 12, marginTop: 20},
});
