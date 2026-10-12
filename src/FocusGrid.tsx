import {NativeSyntheticEvent, requireNativeComponent, ViewProps} from 'react-native';

// 原生处理已挂载卡片的导航，JS 仅负责挂载尚未渲染的目标行。
export const FocusGrid = requireNativeComponent<ViewProps & {
  columns: number; itemCount: number;
  onFocusRequest: (event: NativeSyntheticEvent<{index: number}>) => void;
}>('FocusGridView');
