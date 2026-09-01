import { readLocalFile, requestJson, runAsyncTask } from './api-client';

export interface ReadinessResponse {
  aiReady: boolean;
  ttsReady: boolean;
  sttReady: boolean;
  ttsProvider: string;
  model: string;
  englishVoice: string;
  chineseVoice: string;
}

export interface LessonResponse {
  sessionId: string;
  chineseText: string;
  englishText: string;
  pronunciationTip: string;
  exampleSentence: string;
  exampleTranslation: string;
  question: string;
  questionIndex: number;
  questionCount: number;
  englishAudioPath: string;
  questionAudioPath: string;
}

export interface QuestionResponse {
  questionIndex: number;
  questionCount: number;
  question: string;
  questionAudioPath: string;
}

export interface AttemptResponse {
  attemptId: string;
  recognizedText: string;
  matched: boolean;
  feedback: string;
  feedbackAudioPath: string;
}

export async function getReadiness(): Promise<ReadinessResponse> {
  return requestJson<ReadinessResponse>({
    path: '/api/learning/readiness',
    timeout: 10000
  }, '学习服务暂时开小差了，请稍后再试。');
}

export async function createLesson(chineseText: string): Promise<LessonResponse> {
  return runAsyncTask<LessonResponse>(
    '/api/tasks/learning/sessions',
    { chineseText },
    '学习服务暂时开小差了，请稍后再试。'
  );
}

export async function requestNextQuestion(sessionId: string): Promise<QuestionResponse> {
  return runAsyncTask<QuestionResponse>(
    `/api/tasks/learning/sessions/${encodeURIComponent(sessionId)}/questions/next`,
    undefined,
    '问题生成失败，请稍后再试。'
  );
}

export async function requestPraise(sessionId: string): Promise<AttemptResponse> {
  return runAsyncTask<AttemptResponse>(
    `/api/tasks/learning/sessions/${encodeURIComponent(sessionId)}/praise`,
    undefined,
    '鼓励语音生成失败，请稍后再试。'
  );
}

export async function uploadAttempt(sessionId: string, filePath: string): Promise<AttemptResponse> {
  const audio = await readLocalFile(filePath);
  return runAsyncTask<AttemptResponse>(
    `/api/tasks/learning/sessions/${encodeURIComponent(sessionId)}/attempts`,
    audio,
    '语音识别服务暂时开小差了，请稍后再试。',
    120000,
    { 'content-type': 'application/octet-stream' }
  );
}
