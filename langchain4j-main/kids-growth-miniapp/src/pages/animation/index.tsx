import { useState } from 'react';
import { Button, Image, Input, Picker, Text, View } from '@tarojs/components';
import Taro from '@tarojs/taro';
import animationDream from '../../assets/animation-dream.png';
import WordAnimation from '../../components/word-animation';
import { createAnimationScene, type AnimationSceneResponse } from '../../services/animation';
import './index.scss';

const MODELS = ['童话绘本', '软萌黏土', '彩色蜡笔'];
const STYLES = ['温柔日常', '奇幻冒险', '自然探索'];
const QUICK_WORDS = ['苹果', '小猫', '月亮', '太阳', '星星', '小鸟'];

export default function AnimationPage() {
  const [word, setWord] = useState('');
  const [modelIndex, setModelIndex] = useState(0);
  const [activeStyle, setActiveStyle] = useState(STYLES[0]);
  const [scene, setScene] = useState<AnimationSceneResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [animationKey, setAnimationKey] = useState(0);
  const [playing, setPlaying] = useState(false);

  const generate = async () => {
    const value = word.trim();
    if (!value) {
      Taro.showToast({ title: '先写一个中文单词吧', icon: 'none' });
      return;
    }
    setLoading(true);
    try {
      const result = await createAnimationScene(value, activeStyle);
      setScene(result);
      setAnimationKey((key) => key + 1);
      setPlaying(true);
    } catch (error) {
      Taro.showModal({
        title: '动画还没准备好',
        content: error instanceof Error ? error.message : '请稍后再试。',
        showCancel: false,
        confirmText: '知道啦'
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <View className='page-shell animation-page'>
      <View className='animation-hero'>
        <View className='animation-copy'>
          <Text className='eyebrow'>A LITTLE STORY STUDIO</Text>
          <Text className='page-title'>把单词变成小动画</Text>
          <Text className='page-subtitle'>语芽老师会给小伙伴安排一个会动的欢迎仪式。</Text>
        </View>
        <Image className='animation-image' src={animationDream} mode='aspectFill' />
      </View>

      <View className='scene-note'>
        <View className='coming-icon'>✦</View>
        <View className='scene-note-copy'>
          <Text className='coming-title'>轻轻一点，就能动起来</Text>
          <Text className='coming-note'>动画在小程序本地播放，不用等待视频下载。</Text>
        </View>
      </View>

      <View className='panel'>
        <View className='field-heading'>
          <Text className='field-label'>今天想让谁来玩？</Text>
          <Text className='count'>{word.length} / 30</Text>
        </View>
        <View className='word-input-row'>
          <Input
            className='story-input word-input'
            maxlength={30}
            value={word}
            placeholder='例如：苹果、小猫、月亮'
            onInput={(event) => setWord(event.detail.value)}
          />
          <Button className='generate-button' disabled={loading} onClick={generate}>
            {loading ? '准备中' : '生成动画'}
          </Button>
        </View>
        <View className='quick-list'>
          {QUICK_WORDS.map((item) => (
            <Button key={item} className='quick-chip' onClick={() => setWord(item)}>{item}</Button>
          ))}
        </View>
      </View>

      <View className='panel option-panel'>
        <View className='option-row'>
          <View>
            <Text className='option-label'>动画风格</Text>
            <Text className='option-note'>先用本地模板，之后可接视频模型</Text>
          </View>
          <Picker mode='selector' range={MODELS} value={modelIndex} onChange={(event) => setModelIndex(Number(event.detail.value))}>
            <View className='picker-value'>{MODELS[modelIndex]}⌄</View>
          </Picker>
        </View>
        <Text className='option-label style-label'>故事感觉</Text>
        <View className='style-list'>
          {STYLES.map((style) => (
            <Button
              key={style}
              className={`style-chip ${activeStyle === style ? 'active' : ''}`}
              onClick={() => setActiveStyle(style)}
            >{style}</Button>
          ))}
        </View>
      </View>

      {scene && (
        <View className='panel animation-result'>
          <View className='result-heading'>
            <View>
              <Text className='field-label'>{scene.chineseText} 的小动画</Text>
              <Text className='result-subtitle'>{scene.englishText} · {scene.style}</Text>
            </View>
          </View>
          <WordAnimation
            scene={scene}
            animationKey={animationKey}
            playing={playing}
          />
        </View>
      )}
    </View>
  );
}
