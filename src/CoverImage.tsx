import React, {createContext, useContext, useEffect, useRef, useState} from 'react';
import {Image, ImageProps} from 'react-native';
import {api, keyOf, Video} from './api';

type Entry = {expires: number; users: Set<symbol>; candidates?: Promise<string[]>; requestId?: string; loaded?: string};
const cache = new Map<string, Entry>();
let nextRequestId = 0;
export const CoverSourcesContext = createContext('[]');
const headers = {'User-Agent': 'Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36'};
const identity = (video: Video) => `${keyOf(video)}|${video.vod.vod_pic || ''}|${video.vod.vod_name || ''}|${video.vod.vod_year || ''}|${video.vod.type_name || ''}`;
function entry(key: string): Entry {
  const existing = cache.get(key);
  if (existing && existing.expires > Date.now()) return existing;
  cache.delete(key);
  while (cache.size >= 128) cache.delete(cache.keys().next().value!);
  const created: Entry = {expires: Date.now() + 10 * 60_000, users: new Set()};
  cache.set(key, created);
  return created;
}

type Props = Omit<ImageProps, 'source' | 'onError' | 'onLoad'> & {video: Video; fallback?: React.ReactNode};
function Cover({video, fallback, cacheKey, ...props}: Props & {cacheKey: string}) {
  const [cached] = useState(() => entry(cacheKey));
  const [uri, setUri] = useState(() => cached.loaded || String(video.vod.vod_pic || ''));
  const tried = useRef(new Set<string>());
  const searching = useRef(false);
  const alive = useRef(true);
  const user = useRef(Symbol());
  const retry = useRef<ReturnType<typeof setTimeout>>();
  const busyRetries = useRef(0);
  useEffect(() => {
    alive.current = true;
    cached.users.add(user.current);
    return () => {
      alive.current = false;
      clearTimeout(retry.current);
      cached.users.delete(user.current);
      // 缩略图和详情可能共享任务；最后一个展示者离开后才取消。
      if (!cached.users.size && cached.requestId) {
        api.cancelCoverRequest(cached.requestId);
        delete cached.requestId;
        delete cached.candidates;
      }
    };
  }, [cached]);
  const failed = async () => {
    if (searching.current || !alive.current) return;
    tried.current.add(uri);
    searching.current = true;
    if (cached.loaded === uri) delete cached.loaded;
    // 缩略图、焦点区与详情共享同一次查找，避免重复请求 CMS。
    if (!cached.candidates) {
      const requestId = String(++nextRequestId);
      cached.requestId = requestId;
      cached.candidates = (async () => {
        try {return await api.coverCandidates(video, requestId);}
        finally {if (cached.requestId === requestId) delete cached.requestId;}
      })();
    }
    const request = cached.candidates;
    try {
      const candidates = await request;
      if (alive.current) setUri(candidates.find(url => !tried.current.has(url)) || '');
    } catch (error) {
      // 队列繁忙或网络失败不是“无匹配”，下次展示时应允许重试。
      if (cached.candidates === request) delete cached.candidates;
      if (alive.current) {
        setUri('');
        // 队列腾出位置后重试当前封面；离开页面会清除定时器。
        if ((error as {code?: string})?.code === 'COVER_BUSY') {
          const delay = Math.min(5000, 500 * 2 ** Math.min(busyRetries.current++, 4));
          retry.current = setTimeout(() => void failed(), delay);
        }
      }
    } finally {
      searching.current = false;
    }
  };
  useEffect(() => {if (!uri) void failed();}, []); // 原始图片缺失时也尝试补全。
  return uri ? <Image {...props} key={uri} source={{uri, headers}} onError={() => void failed()}
    onLoad={() => {cached.loaded = uri;}} /> : <>{fallback}</>;
}

export function CoverImage(props: Props) {
  const sources = useContext(CoverSourcesContext);
  const cacheKey = `${sources}|${identity(props.video)}`;
  // 来源配置变更也会重建图片，避免沿用旧来源集合的空结果或图片。
  return <Cover {...props} cacheKey={cacheKey} key={cacheKey} />;
}
