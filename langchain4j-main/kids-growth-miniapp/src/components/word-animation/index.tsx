import { Text, View } from '@tarojs/components';
import type { AnimationSceneResponse } from '../../services/animation';
import './index.scss';

interface WordAnimationProps {
  scene: AnimationSceneResponse;
  animationKey: number;
  playing: boolean;
  compact?: boolean;
}

export default function WordAnimation({
  scene,
  animationKey,
  playing,
  compact = false
}: WordAnimationProps) {
  return (
    <View className={compact ? 'word-animation compact' : 'word-animation'}>
      <View
        key={animationKey}
        className={`animation-stage ${playing ? 'is-playing' : ''}`}
        style={{ backgroundColor: scene.backgroundColor }}
      >
        <View className='scene-cloud cloud-one'>☁</View>
        <View className='scene-cloud cloud-two'>☁</View>
        <View className='scene-sparkle sparkle-one'>✦</View>
        <View className='scene-sparkle sparkle-two'>✧</View>
        <Text className={`scene-object motion-${scene.motion}`}>{scene.emoji}</Text>
        <Text className='scene-word' style={{ color: scene.accentColor }}>{scene.englishText}</Text>
      </View>
      <Text className='scene-caption'>{scene.caption}</Text>
    </View>
  );
}
