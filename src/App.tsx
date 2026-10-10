import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
  AccessibilityInfo, Alert, Animated, AppState, BackHandler, FlatList,
  Pressable, StyleSheet, Text, TextInput, TVFocusGuideView,
  findNodeHandle, useWindowDimensions, View,
} from 'react-native';
import {api, Category, Detail, History, keyOf, Source, titleOf, Video} from './api';
import {ExitDialog} from './ExitDialog';
import {FocusButton} from './FocusButton';
import {SelectionDialog} from './SelectionDialog';
import {CoverImage, CoverSourcesContext} from './CoverImage';

const C = {bg: '#0B1018', panel: '#182230', text: '#F4F7FA', muted: '#96A6BB', accent: '#DCF763'};
const message = (error: unknown) => error instanceof Error ? error.message : String(error);
const field = (video: Video, name: string) => String(video.vod[name] || '');
const description = (video: Video) => field(video, 'vod_content').replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').trim();

function useReducedMotion() {
  const [reduced, setReduced] = useState(true);
  useEffect(() => {
    AccessibilityInfo.isReduceMotionEnabled().then(setReduced).catch(() => {});
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => sub.remove();
  }, []);
  return reduced;
}

function Poster({video, width, onPress, onFocus, preferred, progress, reduced, nextFocusUp, posterRef}: {
  video: Video; width: number; onPress: () => void; onFocus: () => void; preferred?: boolean;
  progress?: History; reduced: boolean;
  nextFocusUp?: number;
  posterRef?: React.Ref<View>;
}) {
  const [focused, setFocused] = useState(false);
  const scale = useRef(new Animated.Value(1)).current;
  const animate = (active: boolean) => {
    setFocused(active);
    scale.stopAnimation();
    if (reduced) scale.setValue(active ? 1.035 : 1);
    else Animated.spring(scale, {toValue: active ? 1.035 : 1, useNativeDriver: true, friction: 9}).start();
  };
  const percent = progress?.duration ? Math.min(100, 100 * (progress.position || 0) / progress.duration) : 0;
  return <Pressable ref={posterRef} accessibilityRole="button" accessibilityLabel={`${titleOf(video)}，${video.source.name}`}
    nextFocusUp={nextFocusUp}
    hasTVPreferredFocus={preferred} onPress={onPress} onFocus={() => {animate(true); onFocus();}}
    onBlur={() => animate(false)} style={{width, margin: 9}}>
    <Animated.View style={[s.poster, focused && s.posterFocus, {height: width * 1.38, transform: [{scale}]}]}>
      <CoverImage video={video} style={StyleSheet.absoluteFill} resizeMode="cover"
        fallback={<View style={s.posterFallback}><Text style={s.fallbackLetter}>▶</Text><Text style={s.fallbackTitle}>{titleOf(video)}</Text></View>} />
      <View style={s.posterBadge}><Text numberOfLines={1} style={s.badgeText}>{field(video, 'vod_remarks') || video.source.name}</Text></View>
      {percent > 0 && <View style={s.progressTrack}><View style={[s.progressFill, {width: `${percent}%`}]} /></View>}
    </Animated.View>
    <Text numberOfLines={1} style={[s.posterTitle, focused && s.accent]}>{titleOf(video)}</Text>
    <Text numberOfLines={1} style={s.posterMeta}>{progress ? `第 ${(progress.episode || 0) + 1} 集 · 继续观看` : `${field(video, 'vod_year')}  ${video.source.name}`}</Text>
  </Pressable>;
}

function Hero({video, detail = false, children}: {video?: Video; detail?: boolean; children?: React.ReactNode}) {
  const compact = useWindowDimensions().height < 600;
  return <View style={[s.hero, detail && s.detailHero, compact && {height: detail ? 235 : 105}]}>
    {video && <CoverImage video={video} blurRadius={16} style={s.heroImage} resizeMode="cover" />}
    <View style={s.heroShade} />
    <View style={[s.heroCopy, compact && {padding: 14, gap: 4}]}>
      <Text style={s.eyebrow}>{detail ? 'LIBRETV / NOW SHOWING' : 'YOUR PERSONAL CINEMA'}</Text>
      <Text numberOfLines={2} style={[s.heroTitle, compact && {fontSize: 24}]}>{video ? titleOf(video) : '把影院，带回家。'}</Text>
      <Text numberOfLines={1} style={s.heroMeta}>{video ? [field(video, 'vod_year'), field(video, 'type_name'), field(video, 'vod_area'), video.source.name].filter(Boolean).join('  ·  ') : '发现故事 / 沉浸观看 / 随时继续'}</Text>
      {(!compact || detail) && <Text numberOfLines={detail ? 3 : 2} style={s.heroDescription}>{video ? description(video) || '选择影片查看详情与播放线路。' : '用遥控器探索你的家庭影院。'}</Text>}
      {children}
    </View>
    {video && <CoverImage video={video} style={[s.heroPoster, detail && !compact && {width: 160, height: 237, top: 24}, compact && !detail && {width: 58, height: 86, top: 9}]} resizeMode="cover" />}
  </View>;
}

export default function App() {
  const {width, height} = useWindowDimensions();
  const reduced = useReducedMotion();
  const columns = Math.max(3, Math.min(7, Math.floor((width - 64) / (height < 600 ? 140 : 150))));
  const posterWidth = (width - 82) / columns - 18;
  const [sources, setSources] = useState<Source[]>([]);
  const coverSources = useMemo(() => JSON.stringify(sources.filter(item => item.enabled).map(item => item.url)), [sources]);
  const [source, setSource] = useState<Source>();
  const [category, setCategory] = useState<Category>({id: '', name: '全部分类'});
  const [categories, setCategories] = useState<Category[]>([]);
  const [query, setQuery] = useState('');
  const input = useRef<TextInput>(null);
  const [inputFocused, setInputFocused] = useState(false);
  const [inputTarget, setInputTarget] = useState<number>();
  const [searchTarget, setSearchTarget] = useState<number>();
  const bindInputTarget = useCallback((node: View | null) => setInputTarget(node ? findNodeHandle(node) || undefined : undefined), []);
  const bindSearchTarget = useCallback((node: View | null) => setSearchTarget(node ? findNodeHandle(node) || undefined : undefined), []);
  const [keyword, setKeyword] = useState('');
  const [videos, setVideos] = useState<Video[]>([]);
  // FlatList 的多列模式用整行影片拼接 key，补齐末行会重建已有海报并丢失焦点。
  const videoRows = useMemo(() => {
    const rows: Video[][] = [];
    for (let index = 0; index < videos.length; index += columns) rows.push(videos.slice(index, index + columns));
    return rows;
  }, [videos, columns]);
  const [hero, setHero] = useState<Video>();
  const [history, setHistory] = useState<History[]>([]);
  const [screen, setScreen] = useState<'home' | 'detail' | 'history'>('home');
  const [detail, setDetail] = useState<Detail>();
  const [detailVideo, setDetailVideo] = useState<Video>();
  const [line, setLine] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [notice, setNotice] = useState('');
  const [picker, setPicker] = useState<'source' | 'category' | 'line' | null>(null);
  const [exitVisible, setExitVisible] = useState(false);
  const [reload, setReload] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const pages = useRef(new Map<string, {next: number; total: number}>());
  const generation = useRef(0);
  const detailGeneration = useRef(0);
  const categoryGeneration = useRef(0);
  const busy = useRef(false);
  const playing = useRef(false);
  const list = useRef<FlatList<Video[]>>(null);
  const lastPoster = useRef<View>(null);
  const entrance = useRef(new Animated.Value(1)).current;
  const entranceStyle = {opacity: entrance, transform: [{translateY: entrance.interpolate({inputRange: [0, 1], outputRange: [12, 0]})}]};
  useEffect(() => {
    entrance.stopAnimation();
    entrance.setValue(reduced ? 1 : 0);
    if (!reduced) Animated.timing(entrance, {toValue: 1, duration: 240, useNativeDriver: true}).start();
    return () => entrance.stopAnimation();
  }, [screen, reduced, entrance]);

  const refreshLocal = useCallback(async () => {
    try {
      const [nextSources, nextHistory] = await Promise.all([api.sources(), api.history()]);
      setSources(old => JSON.stringify(old) === JSON.stringify(nextSources) ? old : nextSources);
      setSource(old => {
        const next = nextSources.find(item => item.url === old?.url && item.enabled) || nextSources.find(item => item.enabled);
        return JSON.stringify(old) === JSON.stringify(next) ? old : next;
      });
      setHistory(nextHistory);
    } catch (err) { setNotice(message(err)); }
  }, []);
  useEffect(() => {
    refreshLocal();
    const sub = AppState.addEventListener('change', state => { if (state === 'active') refreshLocal(); });
    return () => {
      sub.remove(); generation.current++; detailGeneration.current++; categoryGeneration.current++;
      api.cancelRequests('detail');
    };
  }, [refreshLocal]);

  // 播放器保存了新进度时同步续播线路；普通刷新保留用户手动选择的线路。
  useEffect(() => {
    if (!detail) return;
    const row = history.find(item => item.key === keyOf(detail.video));
    if (row) {
      if (row.time !== detail.progress.time) {
        const resumeLine = detail.lines.findIndex(item => item.name === row.line);
        if (resumeLine >= 0) setLine(resumeLine);
      }
      setDetail(old => old ? {...old, progress: row} : old);
    }
  }, [history]);

  const load = useCallback(async (reset: boolean, token: number) => {
    if (busy.current && !reset) return;
    busy.current = true; setLoading(true); setError(''); setNotice('');
    const active = keyword ? sources.filter(item => item.enabled) : source ? [source] : [];
    const eligible = active.filter(item => {const page = pages.current.get(item.url); return !page || page.next <= page.total;});
    if (!eligible.length) {setHasMore(false); setLoading(false); busy.current = false; return;}
    const requests = eligible.map(item => ({source: item, number: pages.current.get(item.url)?.next || 1}));
    const results = await Promise.allSettled(requests.map(item => api.page(item.source, keyword, keyword ? '' : category.id, item.number)));
    if (token !== generation.current) return;
    const batch: Video[] = []; const failures: string[] = [];
    results.forEach((result, i) => {
      const request = requests[i];
      if (result.status === 'fulfilled') {
        batch.push(...result.value.videos);
        pages.current.set(request.source.url, {next: request.number + 1, total: result.value.pages});
      } else failures.push(`${request.source.name}：${message(result.reason)}`);
    });
    setVideos(old => {
      const merged = reset ? [] : [...old]; const seen = new Set(merged.map(keyOf));
      batch.forEach(video => {if (!seen.has(keyOf(video))) {merged.push(video); seen.add(keyOf(video));}});
      return merged;
    });
    if (reset) setHero(batch[0]);
    setHasMore(active.some(item => {const page = pages.current.get(item.url); return !page || page.next <= page.total;}));
    if (failures.length === requests.length) setError(failures.join('\n'));
    else if (failures.length) setNotice(`${failures.length} 个来源请求失败，可用“加载更多 / 重试”重试。`);
    busy.current = false; setLoading(false);
  }, [source, sources, keyword, category.id]);

  useEffect(() => {
    const token = ++generation.current;
    pages.current.clear(); busy.current = false; setVideos([]); setHero(undefined);
    setHasMore(false);
    load(true, token);
    return () => {generation.current++; api.cancelRequests('page');};
  }, [load, reload]);

  useEffect(() => {
    const token = ++categoryGeneration.current; setCategories([]); setCategory({id: '', name: '全部分类'});
    if (source) api.categories(source).then(rows => {if (token === categoryGeneration.current) setCategories(rows);}).catch(() => {});
    return () => {categoryGeneration.current++; api.cancelRequests('categories');};
  }, [source?.url]);

  const openDetail = async (video: Video) => {
    const token = ++detailGeneration.current;
    api.cancelRequests('detail');
    setScreen('detail'); setDetailVideo(video); setDetail(undefined); setLine(0); setDetailError('');
    try {
      const result = await api.detail(video);
      if (token !== detailGeneration.current) return;
      setDetail(result);
      const resumeLine = result.lines.findIndex(item => item.name === result.progress.line);
      setLine(Math.max(0, resumeLine));
    } catch (err) {if (token === detailGeneration.current) setDetailError(message(err));}
  };
  const home = () => {detailGeneration.current++; api.cancelRequests('detail'); setScreen('home'); setDetail(undefined); setDetailVideo(undefined); setDetailError('');};
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (picker) {setPicker(null); return true;}
      if (screen !== 'home') {home(); return true;}
      if (keyword) {setKeyword(''); setQuery(''); return true;}
      setExitVisible(true);
      return true;
    });
    return () => sub.remove();
  }, [screen, picker, keyword]);

  const play = async (episode: number, resume: boolean) => {
    if (!detail || playing.current) return;
    playing.current = true;
    try {await api.play(detail.video, line, episode, resume);}
    catch (err) {Alert.alert('播放失败', message(err));}
    finally {playing.current = false;}
  };
  const search = (text = query) => {
    input.current?.blur();
    const next = text.trim(); setKeyword(next); setQuery(next); setCategory({id: '', name: '全部分类'});
    home(); setReload(value => value + 1);
  };
  const currentLine = detail?.lines[line];
  const resumeEpisode = currentLine && detail?.progress.line === currentLine.name ? Math.max(0, Math.min(detail.progress.episode || 0, currentLine.episodes.length - 1)) : 0;
  const pickerRows = picker === 'source' ? sources.filter(item => item.enabled).map(item => ({id: item.url, name: item.name}))
    : picker === 'category' ? [{id: '', name: '全部分类'}, ...categories]
      : (detail?.lines || []).map((item, i) => ({id: String(i), name: `${item.name} · ${item.episodes.length} 集`}));

  return <CoverSourcesContext.Provider value={coverSources}><View style={s.app}>
    {exitVisible && <ExitDialog reduced={reduced} onDismiss={() => setExitVisible(false)} />}
    <TVFocusGuideView autoFocus style={[s.header, height < 600 && {height: 58}]}>
      <View style={s.brand}><Text style={s.brandIcon}>▶</Text><Text style={s.brandName}>Libre<Text style={s.accent}>TV</Text></Text><Text style={s.brandLabel}>家庭影院</Text></View>
      <View style={s.nav}>
        <FocusButton label="发现" onPress={() => {
          setKeyword(''); setQuery(''); setCategory({id: '', name: '全部分类'});
          home(); setReload(value => value + 1);
        }} />
        <FocusButton label="继续观看" onPress={() => {detailGeneration.current++; api.cancelRequests('detail'); setScreen('history'); refreshLocal();}} />
        <FocusButton label="影视源管理" onPress={api.openSettings} />
      </View>
    </TVFocusGuideView>
    {screen === 'home' && <Animated.View style={[s.content, entranceStyle]}>
      <View style={s.filters}>
        {/* 原生 ReactEditText 不接受方向键请求焦点，通过可聚焦外框承接电视导航。 */}
        <Pressable ref={bindInputTarget} nextFocusRight={searchTarget} style={[s.searchField, inputFocused && s.searchFocus]}
          accessibilityRole="button" accessibilityLabel="影片名称" accessibilityHint="按确认键输入搜索词"
          onFocus={() => setInputFocused(true)} onBlur={() => setInputFocused(false)} onPress={() => input.current?.focus()}>
          <TextInput ref={input} focusable={false} style={s.search} placeholder="搜索影片，探索所有启用来源" placeholderTextColor={C.muted}
            value={query} onChangeText={setQuery} onSubmitEditing={() => search()} returnKeyType="search" accessibilityLabel="输入影片名称"
            onFocus={() => setInputFocused(true)} onBlur={() => setInputFocused(false)} />
        </Pressable>
        <FocusButton label="搜索" buttonRef={bindSearchTarget} nextFocusLeft={inputTarget} onPress={() => search()} />
        <FocusButton label={source?.name || '选择来源'} onPress={() => setPicker('source')} />
        <FocusButton label={category.name} onPress={() => setPicker('category')} disabled={!!keyword} />
      </View>
      <Hero video={hero} />
      <View style={s.sectionHead}><Text style={s.sectionTitle}>{keyword ? `搜索 · ${keyword}` : '发现好故事'}</Text><Text style={s.sectionHint}>{loading ? '正在连接片源…' : `${videos.length} 部影片  /  方向键浏览 · 确定查看`}</Text></View>
      {!!notice && <Text style={s.notice}>{notice}</Text>}
      <TVFocusGuideView autoFocus style={s.listArea}>
        <FlatList ref={list} key={columns} data={videoRows} keyExtractor={row => keyOf(row[0])}
          removeClippedSubviews={false} contentContainerStyle={s.grid} initialNumToRender={2}
          renderItem={({item: row, index: rowIndex}) => <View style={{flexDirection: 'row'}}>
            {row.map((item, columnIndex) => <Poster key={keyOf(item)} video={item} width={posterWidth} reduced={reduced}
              preferred={rowIndex === 0 && columnIndex === 0}
              posterRef={rowIndex * columns + columnIndex === videos.length - 1 ? lastPoster : undefined}
              nextFocusUp={rowIndex === 0 ? inputTarget : undefined}
              onFocus={() => {setHero(item); list.current?.scrollToOffset({offset: rowIndex * (posterWidth * 1.38 + 63), animated: !reduced});}}
              onPress={() => openDetail(item)} />)}
          </View>}
          ListEmptyComponent={<View style={s.empty}>
            {loading ? <View style={s.skeletonRow}>{Array.from({length: columns}, (_, i) => <View key={i} style={[s.skeleton, {width: posterWidth, height: posterWidth * 1.15}]} />)}</View>
              : <><Text style={s.emptyTitle}>{error ? '暂时连接不上片源' : source ? '暂时没有影片' : '添加一个影视源，开始观看'}</Text>
                <Text style={s.emptyText}>{error || '可以换个来源、调整分类或搜索影片。'}</Text>
                <FocusButton label={source ? '重新加载' : '管理影视源'} preferred onPress={source ? () => setReload(value => value + 1) : api.openSettings} /></>}
          </View>}
          ListFooterComponent={videos.length > 0 || hasMore ? <View style={s.footer}>
            {videos.length > 0 && !!error && <Text style={s.emptyText}>{error}</Text>}
            {hasMore ? <FocusButton label={loading ? '正在加载…' : '加载更多 / 重试'} disabled={loading} onPress={() => {
              // 先回到已有列表末项；追加数据沿用影片 key，保留该海报的焦点。
              lastPoster.current?.requestTVFocus();
              load(false, generation.current);
            }} /> : <Text style={s.sectionHint}>已浏览全部影片</Text>}
          </View> : null} />
      </TVFocusGuideView>
    </Animated.View>}
    {screen === 'history' && <Animated.View style={[s.historyContent, entranceStyle]}>
      <View style={s.sectionHead}><View><Text style={s.eyebrow}>PICK UP WHERE YOU LEFT OFF</Text><Text style={s.historyTitle}>继续你的故事</Text></View>
        <FocusButton label="清空记录" onPress={() => Alert.alert('清空观看记录？', '清空后无法恢复。', [{text: '取消', style: 'cancel'}, {text: '清空', onPress: () => api.clearHistory().then(refreshLocal).catch(err => Alert.alert('清空失败', message(err)))}])} /></View>
      <TVFocusGuideView autoFocus style={s.listArea}><FlatList key={columns} data={history} numColumns={columns} keyExtractor={item => item.key} removeClippedSubviews={false}
        renderItem={({item, index}) => <Poster video={item.video} width={posterWidth} progress={item} reduced={reduced} preferred={index === 0} onFocus={() => {}} onPress={() => openDetail(item.video)} />}
        ListEmptyComponent={<View style={s.empty}><Text style={s.emptyTitle}>还没有观看记录</Text><Text style={s.emptyText}>播放影片后，会在这里记住你的进度。</Text><FocusButton label="去发现影片" preferred onPress={home} /></View>} /></TVFocusGuideView>
    </Animated.View>}
    {screen === 'detail' && <Animated.View style={[s.content, entranceStyle]}>
      <Hero video={detail?.video || detailVideo} detail>
        <TVFocusGuideView autoFocus style={s.detailActions}>
          {currentLine && <FocusButton label={detail?.progress.line === currentLine.name ? `继续观看 · ${currentLine.episodes[resumeEpisode]}` : '▶ 立即播放'} primary preferred onPress={() => play(resumeEpisode, true)} />}
          <FocusButton label="返回列表" preferred={!currentLine} onPress={home} />
          {detailVideo && <FocusButton label="搜索其他来源" onPress={() => search(titleOf(detailVideo))} />}
        </TVFocusGuideView>
      </Hero>
      {detail ? currentLine ? <>
        <View style={s.sectionHead}><Text style={s.sectionTitle}>选集 · {currentLine.episodes.length} 集</Text><FocusButton label={`线路 · ${currentLine.name}`} onPress={() => setPicker('line')} /></View>
        <TVFocusGuideView autoFocus style={s.listArea}><FlatList key={line} data={currentLine.episodes} numColumns={6} keyExtractor={(_, i) => String(i)} contentContainerStyle={s.episodeGrid}
          renderItem={({item, index}) => <View style={{width: (width - 88) / 6, padding: 4}}><FocusButton label={item} onPress={() => play(index, false)} /></View>} /></TVFocusGuideView>
      </> : <View style={s.empty}><Text style={s.emptyTitle}>暂无可直接播放的线路</Text><Text style={s.emptyText}>可通过“搜索其他来源”寻找可播放的片源。</Text></View>
        : <View style={s.empty}><Text style={s.emptyTitle}>{detailError ? '详情加载失败' : '正在准备影片…'}</Text><Text style={s.emptyText}>{detailError}</Text>
          {!!detailError && detailVideo && <FocusButton label="重试" onPress={() => openDetail(detailVideo)} />}</View>}
    </Animated.View>}
    {picker && <SelectionDialog key={picker} reduced={reduced} options={pickerRows}
      title={picker === 'source' ? '选择影视来源' : picker === 'category' ? '浏览分类' : '选择播放线路'}
      onDismiss={() => setPicker(null)} onSelect={item => {
          if (picker === 'source') {setSource(sources.find(row => row.url === item.id)); setKeyword(''); setQuery('');}
          else if (picker === 'category') setCategory(item);
          else setLine(Number(item.id));
      }} />}
  </View></CoverSourcesContext.Provider>;
}

const s = StyleSheet.create({
  app: {flex: 1, backgroundColor: C.bg},
  header: {height: 70, paddingHorizontal: 36, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderBottomWidth: 1, borderBottomColor: '#233041'},
  brand: {flexDirection: 'row', alignItems: 'center', gap: 10}, brandIcon: {color: C.accent, fontSize: 24},
  brandName: {color: C.text, fontSize: 26, fontWeight: '800', letterSpacing: -1}, brandLabel: {color: C.muted, fontSize: 12, marginLeft: 8},
  nav: {flexDirection: 'row', gap: 8}, accent: {color: C.accent},
  content: {flex: 1, paddingHorizontal: 32}, filters: {flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 10},
  searchField: {flex: 1, height: 46, borderRadius: 10, borderWidth: 2, borderColor: '#33435B', backgroundColor: '#131D2B'},
  searchFocus: {borderColor: C.accent},
  search: {height: 42, paddingHorizontal: 14, paddingVertical: 0, color: C.text, fontSize: 14},
  hero: {height: 202, borderRadius: 18, overflow: 'hidden', backgroundColor: '#172634'}, detailHero: {height: 285, marginTop: 12},
  heroImage: {position: 'absolute', top: -30, right: 0, width: '100%', height: '150%', opacity: 0.22},
  heroShade: {...StyleSheet.absoluteFillObject, backgroundColor: '#08121B66'},
  heroCopy: {padding: 24, width: '74%', gap: 8}, eyebrow: {color: C.accent, fontSize: 10, letterSpacing: 3, fontWeight: '700'},
  heroTitle: {color: C.text, fontSize: 32, fontWeight: '800'}, heroMeta: {color: '#BACDDD', fontSize: 12},
  heroDescription: {color: C.muted, fontSize: 13, lineHeight: 20, maxWidth: 680},
  heroPoster: {position: 'absolute', right: 32, top: 14, width: 117, height: 174, borderRadius: 10, borderWidth: 1, borderColor: '#FFFFFF33'},
  detailActions: {flexDirection: 'row', gap: 10, marginTop: 4},
  sectionHead: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 12},
  sectionTitle: {color: C.text, fontSize: 18, fontWeight: '700'}, sectionHint: {color: C.muted, fontSize: 11},
  notice: {color: '#E4C997', fontSize: 12, marginBottom: 6}, listArea: {flex: 1}, grid: {paddingBottom: 24, paddingHorizontal: 0},
  poster: {borderRadius: 12, overflow: 'hidden', backgroundColor: C.panel, borderWidth: 2, borderColor: '#2A3545'},
  posterFocus: {borderColor: C.accent, elevation: 10}, posterFallback: {flex: 1, justifyContent: 'center', alignItems: 'center', padding: 12},
  fallbackLetter: {color: '#415572', fontSize: 38}, fallbackTitle: {color: C.muted, fontSize: 14, textAlign: 'center', marginTop: 12},
  posterBadge: {position: 'absolute', bottom: 0, left: 0, right: 0, padding: 8, backgroundColor: '#08101DE8'}, badgeText: {color: C.text, fontSize: 10},
  posterTitle: {color: C.text, fontSize: 14, fontWeight: '700', marginTop: 10}, posterMeta: {color: C.muted, fontSize: 10, marginTop: 4},
  progressTrack: {height: 3, backgroundColor: '#33435B', position: 'absolute', bottom: 0, left: 0, right: 0}, progressFill: {height: 3, backgroundColor: C.accent},
  empty: {padding: 24, alignItems: 'center', gap: 10}, emptyTitle: {color: C.text, fontSize: 20, fontWeight: '700'},
  emptyText: {color: C.muted, fontSize: 13, lineHeight: 20, textAlign: 'center'}, footer: {alignItems: 'center', padding: 20, gap: 8},
  skeletonRow: {flexDirection: 'row', gap: 18}, skeleton: {backgroundColor: '#192738', borderRadius: 12},
  historyContent: {flex: 1, paddingHorizontal: 36, paddingTop: 22}, historyTitle: {fontSize: 30, fontWeight: '800', color: C.text, marginTop: 10},
  episodeGrid: {paddingBottom: 20},
});
