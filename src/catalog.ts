import type {Category, Page, Source, Video} from './api';

export type PlaybackLine = {name: string; episodes: {name: string; url: string}[]};
type RecordData = Record<string, unknown>;
const record = (value: unknown): value is RecordData => !!value && typeof value === 'object' && !Array.isArray(value);
const string = (value: unknown) => value == null ? '' : String(value);
const adult = (type: unknown) => /伦理|倫理|福利|色情|无码|有码|里番|传媒|写真|萝莉|诱惑|擦边|成人视频|cosplay|swag/i.test(string(type));
// 不依赖 Hermes 中不完整的 URL 实现；原生请求层还会验证实际地址。
const http = (url: string) => /^https?:\/\/[^\s/?#]+(?:[/?#][^\s]*)?$/.test(url);
const normalize = (value: unknown) => string(value).normalize('NFKC').trim().replace(/\s+/g, ' ');
const encode = (value: string) => encodeURIComponent(value).replace(/[!'()~]/g, char => `%${char.charCodeAt(0).toString(16).toUpperCase()}`).replace(/%20/g, '+');

export function cmsAddress(source: Source, params: Record<string, string>): string {
  let address = source.url.split('#', 1)[0];
  for (const [key, value] of Object.entries(params)) address += `${address.includes('?') ? '&' : '?'}${encode(key)}=${encode(value)}`;
  return address;
}

export function parsePage(source: Source, data: RecordData): Page {
  if (data.list != null && !Array.isArray(data.list)) throw new Error('列表格式无效');
  const count = Number(data.pagecount);
  return {
    pages: Number.isFinite(count) ? Math.max(1, Math.trunc(count)) : 1,
    videos: (Array.isArray(data.list) ? data.list : []).filter(vod => record(vod) && string(vod.vod_id) && !adult(vod.type_name))
      .map(vod => ({source, vod: vod as Video['vod']})),
  };
}

export function parseCategories(data: RecordData): Category[] {
  const rows = Array.isArray(data.class) ? data.class : [];
  if (!rows.every(record)) throw new Error('分类格式无效');
  const parents = new Set(rows.map(row => string(row.type_pid)).filter(id => id && id !== '0'));
  return rows.filter(row => !adult(row.type_name) && !parents.has(string(row.type_id)))
    .map(row => ({id: string(row.type_id), name: string(row.type_name)}));
}

export function playbackLines(video: Video): PlaybackLine[] {
  const names = string(video.vod.vod_play_from).split('$$$');
  return string(video.vod.vod_play_url).split('$$$').map((group, index) => ({
    name: names[index] ?? `线路 ${index + 1}`,
    episodes: group.split('#').flatMap(entry => {
      const split = entry.indexOf('$');
      if (split < 0) return [];
      const url = entry.slice(split + 1).trim();
      const path = url.replace(/^https?:\/\/[^/?#]+/, '').split(/[?#]/, 1)[0];
      return http(url) && /\.(m3u8|mp4|m4v|mkv|webm|mpd|ts)$/i.test(path) ? [{name: entry.slice(0, split), url}] : [];
    }),
  })).filter(line => line.episodes.length > 0);
}

export function sameFilm(original: Video, candidate: Video): boolean {
  const name = normalize(original.vod.vod_name), year = string(original.vod.vod_year).trim(), type = normalize(original.vod.type_name);
  return !!name && /^\d{4}$/.test(year) && !!type && name === normalize(candidate.vod.vod_name)
    && year === string(candidate.vod.vod_year).trim() && type === normalize(candidate.vod.type_name);
}

type Transport = {fetch: (address: string, group: string) => Promise<string>; cancel: (group: string) => void; sources: () => Promise<Source[]>};
type Scope = {cancelled: boolean};
export function createCatalog(transport: Transport) {
  const pending = new Map<string, Set<Scope>>();
  const cancelled = () => Object.assign(new Error('请求已取消'), {code: 'REQUEST_CANCELLED'});
  async function run<T>(group: string, task: (request: (source: Source, params: Record<string, string>) => Promise<RecordData>, check: () => void) => Promise<T>): Promise<T> {
    const scope = {cancelled: false};
    const scopes = pending.get(group) || new Set<Scope>();
    pending.set(group, scopes); scopes.add(scope);
    const check = () => {if (scope.cancelled) throw cancelled();};
    const request = async (source: Source, params: Record<string, string>) => {
      check();
      const body = await transport.fetch(cmsAddress(source, params), group);
      check();
      let data: unknown;
      try {data = JSON.parse(body.replace(/^\uFEFF/, ''));} catch {throw new Error('接口未返回苹果 CMS JSON 数据');}
      if (!record(data)) throw new Error('接口未返回苹果 CMS JSON 数据');
      return data;
    };
    try {const result = await task(request, check); check(); return result;}
    finally {scopes.delete(scope); if (!scopes.size) pending.delete(group);}
  }
  const page = (source: Source, keyword: string, category: string, number: number, group = 'page') => run(group, async request => {
    const params: Record<string, string> = {ac: 'videolist', pg: String(Math.max(1, number))};
    if (keyword) params.wd = keyword;
    if (category) params.t = category;
    return parsePage(source, await request(source, params));
  });
  return {
    page,
    cancel(group: string) {
      // 顺序补全封面可能正处于两个 HTTP 请求之间，也要阻止后续请求。
      pending.get(group)?.forEach(scope => {scope.cancelled = true;});
      transport.cancel(group);
    },
    probeSource: async (source: Source) => ({count: (await page(source, '', '', 1, 'probe')).videos.length}),
    categories: (source: Source) => run('categories', async request => parseCategories(await request(source, {ac: 'list'}))),
    detail: (video: Video) => run('detail', async request => {
      const data = await request(video.source, {ac: 'videolist', ids: string(video.vod.vod_id)});
      if (!Array.isArray(data.list) || !data.list.length) throw new Error('影片详情为空');
      if (!record(data.list[0])) throw new Error('影片详情格式无效');
      return {source: video.source, vod: data.list[0] as Video['vod']};
    }),
    coverCandidates: (video: Video, requestId: string) => run(`cover:${requestId}`, async (request, check) => {
      const sources = await transport.sources(); check();
      const urls = new Set<string>();
      const failedUrl = string(video.vod.vod_pic).trim();
      let failure: unknown;
      for (const source of sources.filter(item => item.enabled && item.url !== video.source.url).slice(0, 4)) {
        try {
          const data = await request(source, {ac: 'videolist', pg: '1', wd: string(video.vod.vod_name) || '未命名影片'});
          for (const candidate of parsePage(source, data).videos) {
            const url = string(candidate.vod.vod_pic).trim();
            if (sameFilm(video, candidate) && http(url) && url !== failedUrl) urls.add(url);
          }
        } catch (error) {check(); failure = error;}
      }
      // 部分来源失败时，空结果不能作为确定的“无匹配”缓存。
      if (!urls.size && failure) throw failure;
      return [...urls];
    }),
  };
}
