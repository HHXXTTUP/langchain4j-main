import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Image, Input, Text, View } from '@tarojs/components';
import Taro from '@tarojs/taro';
import WordAnimation from '../../components/word-animation';
import {
  createLesson,
  getReadiness,
  requestNextQuestion,
  requestPraise,
  uploadAttempt,
  type AttemptResponse,
  type LessonResponse,
  type ReadinessResponse
} from '../../services/learning';
import { createAnimationScene, type AnimationSceneResponse } from '../../services/animation';
import { downloadAudioFile } from '../../services/api-client';
import englishBuddy from '../../assets/english-buddy.png';
import './index.scss';

const QUICK_WORDS = ['苹果', '小猫', '月亮', '开心'];

export default function EnglishPage() {
  const [chineseText, setChineseText] = useState('');
  const [lesson, setLesson] = useState<LessonResponse | null>(null);
  const [currentQuestion, setCurrentQuestion] = useState('');
  const [questionIndex, setQuestionIndex] = useState(0);
  const [questionAudioPath, setQuestionAudioPath] = useState('');
  const [feedback, setFeedback] = useState<AttemptResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [questionLoading, setQuestionLoading] = useState(false);
  const [recording, setRecording] = useState(false);
  const [recognizing, setRecognizing] = useState(false);
  const [readiness, setReadiness] = useState<ReadinessResponse | null>(null);
  const [animationScene, setAnimationScene] = useState<AnimationSceneResponse | null>(null);
  const [animationLoading, setAnimationLoading] = useState(false);
  const [animationError, setAnimationError] = useState('');
  const [animationKey, setAnimationKey] = useState(0);
  const [animationPlaying, setAnimationPlaying] = useState(false);
  const audioRef = useRef<Taro.InnerAudioContext | null>(null);
  const audioFileCleanupRef = useRef<(() => void) | null>(null);
  const audioRequestRef = useRef(0);
  const sessionIdRef = useRef('');
  // Delay recorder creation until it is actually needed. Some DevTools runtimes
  // do not expose the recorder API during the first page render.
  const recorderRef = useRef<ReturnType<typeof Taro.getRecorderManager> | null>(null);
  const recorderListenersAttachedRef = useRef(false);

  const stopAudio = useCallback(() => {
    audioRequestRef.current += 1;
    if (audioRef.current) {
      audioRef.current.stop();
      audioRef.current.destroy();
      audioRef.current = null;
    }
    audioFileCleanupRef.current?.();
    audioFileCleanupRef.current = null;
  }, []);

  const playAudio = useCallback(async (path: string, onEnded?: () => void) => {
    if (!path) return;
    stopAudio();
    const requestId = audioRequestRef.current;
    try {
      const download = await downloadAudioFile(path);
      if (requestId !== audioRequestRef.current) {
        download.cleanup();
        return;
      }

      const audio = Taro.createInnerAudioContext();
      audioRef.current = audio;
      audioFileCleanupRef.current = download.cleanup;
      audio.autoplay = false;
      let started = false;
      const releaseAudioFile = () => {
        download.cleanup();
        if (audioFileCleanupRef.current === download.cleanup) {
          audioFileCleanupRef.current = null;
        }
      };
      const startPlayback = () => {
        if (started || audioRef.current !== audio) return;
        started = true;
        audio.play();
      };
      audio.onCanplay(startPlayback);
      audio.onEnded(() => {
        audio.destroy();
        if (audioRef.current === audio) audioRef.current = null;
        releaseAudioFile();
        onEnded?.();
      });
      audio.onError((error) => {
        audio.destroy();
        if (audioRef.current === audio) audioRef.current = null;
        releaseAudioFile();
        Taro.showToast({
          title: error?.errMsg || '语音播放失败，请检查后端地址',
          icon: 'none'
        });
      });
      audio.src = download.filePath;
      setTimeout(startPlayback, 120);
    } catch (error) {
      if (requestId !== audioRequestRef.current) return;
      Taro.showToast({
        title: error instanceof Error ? error.message : '语音下载失败，请检查后端地址',
        icon: 'none'
      });
    }
  }, [stopAudio]);

  useEffect(() => {
    getReadiness().then(setReadiness).catch(() => setReadiness(null));
    return stopAudio;
  }, [stopAudio]);

  useEffect(() => {
    const recorder = recorderRef.current;
    if (!recorder || recorderListenersAttachedRef.current) return;
    recorderListenersAttachedRef.current = true;
    recorder.onStop(async ({ tempFilePath }) => {
      setRecording(false);
      const sessionId = sessionIdRef.current;
      if (!sessionId) return;
      setRecognizing(true);
      try {
        const result = await uploadAttempt(sessionId, tempFilePath);
        setFeedback(result);
        playAudio(result.feedbackAudioPath);
      } catch (error) {
        Taro.showModal({
          title: '暂时没听清',
          content: error instanceof Error ? error.message : '请稍后再试。',
          showCancel: false,
          confirmText: '知道啦'
        });
      } finally {
        setRecognizing(false);
      }
    });
    recorder.onError(() => {
      setRecording(false);
      setRecognizing(false);
      Taro.showToast({ title: '录音没有成功，请检查麦克风权限', icon: 'none' });
    });
  }, [playAudio, recording]);

  const loadAnimation = async (chineseText: string, englishText: string) => {
    setAnimationLoading(true);
    setAnimationError('');
    try {
      const result = await createAnimationScene(chineseText, '温柔日常', englishText);
      setAnimationScene(result);
      setAnimationKey((key) => key + 1);
      setAnimationPlaying(true);
    } catch (error) {
      setAnimationScene(null);
      setAnimationError(error instanceof Error ? error.message : '小动画暂时没有准备好');
    } finally {
      setAnimationLoading(false);
    }
  };

  const startLesson = async () => {
    const value = chineseText.trim();
    if (!value) {
      Taro.showToast({ title: '先写一个中文单词吧', icon: 'none' });
      return;
    }
    stopAudio();
    setLoading(true);
    try {
      const result = await createLesson(value);
      sessionIdRef.current = result.sessionId;
      setFeedback(null);
      setAnimationScene(null);
      setAnimationError('');
      setAnimationPlaying(false);
      setLesson(result);
      setCurrentQuestion(result.question);
      setQuestionIndex(result.questionIndex);
      setQuestionAudioPath(result.questionAudioPath);
      void loadAnimation(result.chineseText, result.englishText);
      setTimeout(() => {
        playAudio(result.englishAudioPath, () => playAudio(result.questionAudioPath));
      }, 250);
    } catch (error) {
      const message = error instanceof Error ? error.message : '请检查后端服务。';
      const temporaryFailure = message === '服务开小差了~';
      Taro.showModal({
        title: temporaryFailure ? '服务开小差了~' : '课程还没准备好',
        content: temporaryFailure ? '我先休息一下，请稍后再试。' : message,
        showCancel: false,
        confirmText: '知道啦'
      });
    } finally {
      setLoading(false);
    }
  };

  const nextQuestion = async () => {
    if (!lesson || questionLoading) return;
    setQuestionLoading(true);
    setFeedback(null);
    try {
      const result = await requestNextQuestion(lesson.sessionId);
      setCurrentQuestion(result.question);
      setQuestionIndex(result.questionIndex);
      setQuestionAudioPath(result.questionAudioPath);
      playAudio(result.questionAudioPath);
    } catch (error) {
      Taro.showToast({
        title: error instanceof Error ? error.message : '问题生成失败',
        icon: 'none'
      });
    } finally {
      setQuestionLoading(false);
    }
  };

  const startRecording = async () => {
    if (!lesson || recording || recognizing) return;
    if (typeof Taro.getRecorderManager !== 'function') {
      Taro.showToast({ title: '当前开发工具暂不支持录音', icon: 'none' });
      return;
    }
    try {
      await Taro.authorize({ scope: 'scope.record' });
    } catch {
      const modal = await Taro.showModal({
        title: '需要麦克风权限',
        content: '开启麦克风后，语芽老师才能听见宝宝读英语。',
        confirmText: '去设置'
      });
      if (modal.confirm) await Taro.openSetting();
      return;
    }
    setFeedback(null);
    setRecording(true);
    const recorder = recorderRef.current || Taro.getRecorderManager();
    recorderRef.current = recorder;
    recorder.start({
      duration: 8000,
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 48000,
      format: 'wav'
    });
  };

  const stopRecording = () => {
    if (recording) recorderRef.current?.stop();
  };

  const praiseWithoutRecording = async () => {
    if (!lesson || recognizing) return;
    setRecognizing(true);
    try {
      const result = await requestPraise(lesson.sessionId);
      setFeedback(result);
      playAudio(result.feedbackAudioPath);
    } catch (error) {
      Taro.showToast({
        title: error instanceof Error ? error.message : '鼓励语音生成失败',
        icon: 'none'
      });
    } finally {
      setRecognizing(false);
    }
  };

  return (
    <View className='page-shell english-page'>
      <View className='hero'>
        <View className='hero-copy'>
          <Text className='eyebrow'>HELLO, LITTLE STAR</Text>
          <Text className='page-title'>和语芽老师学英语</Text>
          <Text className='page-subtitle'>听一听、说一说，每次开口都值得鼓励。</Text>
        </View>
        <Image className='hero-image' src={englishBuddy} mode='aspectFill' />
      </View>

      {readiness && (!readiness.aiReady || !readiness.ttsReady) && (
        <View className='readiness-warning'>
          <Text className='warning-title'>服务还差一点配置</Text>
          <Text className='warning-copy'>
            {!readiness.aiReady ? '请配置 GLM 密钥。' : ''}
            {!readiness.ttsReady ? '请先安装 edge-tts 并重启后端。' : ''}
          </Text>
        </View>
      )}

      <View className='panel word-panel'>
        <Text className='field-label'>今天想学什么？</Text>
        <View className='input-row'>
          <Input
            className='word-input'
            value={chineseText}
            maxlength={30}
            placeholder='例如：苹果、小猫、月亮'
            onInput={(event) => setChineseText(event.detail.value)}
          />
          <Button className='start-button' disabled={loading} onClick={startLesson}>
            {loading ? '准备中' : '开始'}
          </Button>
        </View>
        <View className='quick-list'>
          {QUICK_WORDS.map((word) => (
            <Button key={word} className='quick-chip' onClick={() => setChineseText(word)}>{word}</Button>
          ))}
        </View>
      </View>

      {!lesson && (
        <View className='welcome-dialogue'>
          <View className='teacher-avatar'>芽</View>
          <View className='bubble teacher-bubble'>
            <Text>告诉我一个中文单词，我会陪你听英文、读英文，再玩几个小问题。</Text>
          </View>
        </View>
      )}

      {lesson && (
        <View className='lesson-conversation'>
          <View className='dialogue-row child-row'>
            <View className='bubble child-bubble'><Text>我想学：{lesson.chineseText}</Text></View>
            <View className='child-avatar'>我</View>
          </View>

          <View className='dialogue-row'>
            <View className='teacher-avatar'>芽</View>
            <View className='bubble teacher-bubble'>
              <Text>好呀！这个词的英文是：</Text>
            </View>
          </View>

          <View className='english-card'>
            <View className='english-main'>
              <Text className='english-word'>{lesson.englishText}</Text>
              <Text className='pronunciation-tip'>{lesson.pronunciationTip}</Text>
            </View>
            <Button className='sound-button' onClick={() => playAudio(lesson.englishAudioPath)}>▶</Button>
            <View className='example-box'>
              <Text className='example-english'>{lesson.exampleSentence}</Text>
              <Text className='example-chinese'>{lesson.exampleTranslation}</Text>
            </View>
          </View>

          <View className='lesson-animation-panel'>
            <View className='lesson-animation-heading'>
              <View>
                <Text className='lesson-animation-title'>单词小动画</Text>
                <Text className='lesson-animation-note'>让 {lesson.englishText} 动起来</Text>
              </View>
            </View>
            {animationLoading && (
              <Text className='lesson-animation-status'>正在给你画一个小伙伴…</Text>
            )}
            {animationError && (
              <View className='lesson-animation-error'>
                <Text>{animationError}</Text>
                <Button className='lesson-animation-retry' onClick={() => void loadAnimation(lesson.chineseText, lesson.englishText)}>
                  再试一次
                </Button>
              </View>
            )}
            {animationScene && (
              <WordAnimation
                scene={animationScene}
                animationKey={animationKey}
                playing={animationPlaying}
                compact
              />
            )}
          </View>

          <View className='dialogue-row'>
            <View className='teacher-avatar'>芽</View>
            <View className='bubble teacher-bubble question-bubble'>
              <Text>{currentQuestion}</Text>
              <Button className='inline-sound' onClick={() => playAudio(questionAudioPath)}>再听一次</Button>
            </View>
          </View>

          <View className='speaking-panel'>
            <Text className='speaking-title'>轮到宝宝读啦</Text>
            <Text className='speaking-note'>请大声读出 “{lesson.englishText}”。</Text>
            {readiness?.sttReady && (
              <Button
                className={`record-button ${recording ? 'recording' : ''}`}
                disabled={recognizing}
                onClick={recording ? stopRecording : startRecording}
              >
                <Text className='record-symbol'>{recording ? '■' : '●'}</Text>
                {recognizing ? '正在认真听…' : recording ? '读完啦' : '开始读英语'}
              </Button>
            )}
            <Button className='fallback-button' disabled={recognizing} onClick={praiseWithoutRecording}>
              我读完啦，给我一颗小星星
            </Button>
          </View>

          {feedback && (
            <View className='dialogue-row feedback-row'>
              <View className='teacher-avatar happy'>★</View>
              <View className='bubble praise-bubble'>
                <Text>{feedback.feedback}</Text>
                {feedback.recognizedText && (
                  <Text className='recognized-text'>我听到：{feedback.recognizedText}</Text>
                )}
              </View>
            </View>
          )}

          {questionIndex + 1 < lesson.questionCount && (
            <Button className='secondary-button next-question-button' loading={questionLoading} onClick={nextQuestion}>
              换一个小问题
            </Button>
          )}
        </View>
      )}
    </View>
  );
}
