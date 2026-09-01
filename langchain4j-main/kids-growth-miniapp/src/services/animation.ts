import { runAsyncTask } from './api-client';

export interface AnimationSceneResponse {
  chineseText: string;
  englishText: string;
  template: string;
  objectLabel: string;
  emoji: string;
  caption: string;
  accentColor: string;
  backgroundColor: string;
  motion: string;
  style: string;
  durationMs: number;
}

export async function createAnimationScene(
  chineseText: string,
  style: string,
  englishText?: string
): Promise<AnimationSceneResponse> {
  return runAsyncTask<AnimationSceneResponse>(
    '/api/tasks/animation/scenes',
    { chineseText, englishText, style },
    '动画服务暂时开小差了，请稍后再试。'
  );
}
