import React, {useEffect, useRef, useState} from 'react';
import {ScrollView, StyleSheet, Text, TVFocusGuideView, View} from 'react-native';
import {api, Source} from './api';
import {FocusButton} from './FocusButton';
import {FocusCheckbox} from './FocusCheckbox';
import {AddSourceDialog} from './AddSourceDialog';
import {DialogMessage, MessageDialog} from './MessageDialog';

const message = (error: unknown) => error instanceof Error ? error.message : String(error);
const about = '基于 LibreSpark/LibreTV 的苹果 CMS 协议，React Native TV 界面与原生播放器。\n\n无账号、无启动密码、无自建后端。数据与视频由第三方源直接提供，观看记录仅保存在本机。\n\n上游：https://github.com/LibreSpark/LibreTV\n许可：GNU AGPL-3.0，源码随项目提供。\n播放器：AndroidX Media3（Apache-2.0）。';

export function SettingsScreen({sources, reduced, onBack, onChanged}: {
  sources: Source[]; reduced: boolean; onBack: () => void; onChanged: () => Promise<void>;
}) {
  const [adding, setAdding] = useState(false);
  const [notice, setNotice] = useState<DialogMessage | null>(null);
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const probeGeneration = useRef(0);
  useEffect(() => () => {probeGeneration.current++; api.cancelProbe();}, []);
  const save = async (next: Source[]) => {
    if (saving.current) return;
    saving.current = true; setBusy(true);
    try {await api.saveSources(next); await onChanged();}
    finally {saving.current = false; setBusy(false);}
  };
  const update = (next: Source[]) => save(next).catch(error => setNotice({title: '保存失败', message: message(error)}));
  const probe = async (source: Source) => {
    const token = ++probeGeneration.current;
    setNotice({title: '来源检测', message: `正在检测 ${source.name}…`});
    try {
      const result = await api.probeSource(source);
      if (token === probeGeneration.current) setNotice({title: '来源检测', message: `${source.name} 可用，返回 ${result.count} 部影视`});
    } catch (error) {
      if (token === probeGeneration.current) setNotice({title: '来源检测', message: `${source.name}：${message(error)}`});
    }
  };
  return <View style={s.screen}>
    <Text style={s.title}>LibreTV. / 影视源管理</Text>
    <Text style={s.help}>管理你的影视来源。启用的来源参与搜索，首页可单独选择浏览来源。</Text>
    <TVFocusGuideView autoFocus style={s.actions}>
      <FocusButton label="返回首页" preferred onPress={onBack} />
      <FocusButton label="添加来源" disabled={busy} onPress={() => setAdding(true)} />
      <FocusButton label="关于" onPress={() => setNotice({title: `LibreTV 家庭影院 ${api.version}`, message: about})} />
    </TVFocusGuideView>
    <ScrollView contentContainerStyle={s.list}>
      {sources.map(source => <View key={source.url} style={s.source}>
        <View style={s.row}>
          <View style={s.name}><FocusCheckbox label={source.name} checked={source.enabled} reduced={reduced} disabled={busy}
            onChange={enabled => void update(sources.map(row => row.url === source.url ? {...row, enabled} : row))} /></View>
          <FocusButton label="检测" onPress={() => void probe(source)} />
          <FocusButton label="删除" disabled={busy} onPress={() => setNotice({title: `删除“${source.name}”？`, confirm: '删除',
            onConfirm: () => void update(sources.filter(row => row.url !== source.url))})} />
        </View>
        <Text style={s.url}>{source.url}</Text>
      </View>)}
      {!sources.length && <Text style={s.help}>还没有影视源，使用“添加来源”配置接口。</Text>}
    </ScrollView>
    {adding && <AddSourceDialog reduced={reduced} sources={sources} onDismiss={() => setAdding(false)} onSave={source => save([...sources, source])} />}
    {notice && <MessageDialog {...notice} reduced={reduced} onDismiss={() => {probeGeneration.current++; api.cancelProbe(); setNotice(null);}} />}
  </View>;
}

const s = StyleSheet.create({
  screen: {flex: 1, paddingHorizontal: 36, paddingVertical: 20},
  title: {color: '#F4F7FA', fontSize: 27, fontWeight: '700'},
  help: {color: '#96A6BB', fontSize: 15, lineHeight: 24, marginTop: 12},
  actions: {flexDirection: 'row', gap: 10, paddingVertical: 14},
  list: {paddingBottom: 16}, source: {marginBottom: 14},
  row: {flexDirection: 'row', alignItems: 'center', gap: 10},
  name: {flex: 1, alignItems: 'flex-start'},
  url: {color: '#96A6BB', fontSize: 12, marginTop: 4, paddingLeft: 8},
});
