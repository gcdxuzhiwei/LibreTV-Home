import {NativeModules} from 'react-native';
import {createCatalog, playbackLines} from './catalog';

export type Source = {name: string; url: string; enabled: boolean};
export type Video = {source: Source; vod: Record<string, string | number>};
export type Page = {videos: Video[]; pages: number};
export type Category = {id: string; name: string};
export type Progress = {line?: string; episode?: number; position?: number; duration?: number; time?: number};
export type History = Progress & {key: string; video: Video; time: number};
export type Detail = {video: Video; lines: {name: string; episodes: string[]}[]; progress: Progress};
const native = NativeModules.LibreTV;
const json = async <T,>(task: Promise<string>): Promise<T> => JSON.parse(await task);
const catalog = createCatalog({
  fetch: (address, group) => native.fetchCms(address, group),
  cancel: group => native.cancelRequests(group),
  sources: () => json<Source[]>(native.sources()),
});
export const keyOf = (video: Video) => `${video.source.url}|${video.vod.vod_id}`;
export const titleOf = (video: Video) => String(video.vod.vod_name || '未命名影片');
export const api = {
  cancelRequests: (group: 'page' | 'categories' | 'detail') => catalog.cancel(group),
  sources: () => json<Source[]>(native.sources()),
  saveSources: (sources: Source[]): Promise<void> => native.saveSources(JSON.stringify(sources)),
  probeSource: catalog.probeSource,
  cancelProbe: () => catalog.cancel('probe'),
  version: String(native.version),
  coverCandidates: catalog.coverCandidates,
  cancelCoverRequest: (requestId: string) => catalog.cancel(`cover:${requestId}`),
  page: catalog.page,
  categories: catalog.categories,
  detail: async (video: Video): Promise<Detail> => {
    const detailed = await catalog.detail(video);
    const history = await json<History[]>(native.history());
    return {video: detailed, lines: playbackLines(detailed).map(line => ({name: line.name, episodes: line.episodes.map(episode => episode.name)})),
      progress: history.find(row => row.key === keyOf(detailed)) || {}};
  },
  history: () => json<History[]>(native.history()),
  clearHistory: (): Promise<void> => native.clearHistory(),
  play: (video: Video, line: number, episode: number, resume: boolean): Promise<void> =>
    native.play(JSON.stringify({...video, playbackLines: playbackLines(video)}), line, episode, resume),
};
