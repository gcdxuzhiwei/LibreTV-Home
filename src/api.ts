import {NativeModules} from 'react-native';

export type Source = {name: string; url: string; enabled: boolean};
export type Video = {source: Source; vod: Record<string, string | number>};
export type Page = {videos: Video[]; pages: number};
export type Category = {id: string; name: string};
export type Progress = {line?: string; episode?: number; position?: number; duration?: number; time?: number};
export type History = Progress & {key: string; video: Video; time: number};
export type Detail = {video: Video; lines: {name: string; episodes: string[]}[]; progress: Progress};
const native = NativeModules.LibreTV;
const json = async <T,>(task: Promise<string>): Promise<T> => JSON.parse(await task);
export const keyOf = (video: Video) => `${video.source.url}|${video.vod.vod_id}`;
export const titleOf = (video: Video) => String(video.vod.vod_name || '未命名影片');
export const api = {
  cancelRequests: (group: 'page' | 'categories' | 'detail') => native.cancelRequests(group),
  sources: () => json<Source[]>(native.sources()),
  coverCandidates: (video: Video, requestId: string) => json<string[]>(native.coverCandidates(JSON.stringify(video), requestId)),
  cancelCoverRequest: (requestId: string) => native.cancelRequests(`cover:${requestId}`),
  page: (source: Source, keyword: string, category: string, page: number) =>
    json<Page>(native.page(JSON.stringify(source), keyword, category, page)),
  categories: (source: Source) => json<Category[]>(native.categories(JSON.stringify(source))),
  detail: (video: Video) => json<Detail>(native.detail(JSON.stringify(video))),
  history: () => json<History[]>(native.history()),
  clearHistory: (): Promise<void> => native.clearHistory(),
  play: (video: Video, line: number, episode: number, resume: boolean): Promise<void> =>
    native.play(JSON.stringify(video), line, episode, resume),
  openSettings: () => native.openSettings(),
};
