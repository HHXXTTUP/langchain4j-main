import Taro from '@tarojs/taro';
import {
  apiUrl,
  CLOUD_ENV_ID,
  CLOUD_SERVICE_NAME,
  USE_CLOUD_CONTAINER
} from '../config/runtime';

type ApiMethod = 'GET' | 'POST';
type RequestData = string | Record<string, unknown> | ArrayBuffer;

interface ApiResponse<T> {
  statusCode: number;
  data: T;
}

interface RequestOptions {
  path: string;
  method?: ApiMethod;
  data?: RequestData;
  header?: Record<string, string>;
  responseType?: 'text' | 'arraybuffer';
  timeout?: number;
}

interface ApiError {
  message?: string;
}

interface TaskSubmission {
  taskId: string;
}

interface TaskSnapshot<T> {
  taskId: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  result?: T;
  error?: string;
}

export interface DownloadedAudioFile {
  filePath: string;
  cleanup: () => void;
}

let cloudInitialization: Promise<void> | null = null;

export function initializeCloudContainer(): Promise<void> {
  if (!USE_CLOUD_CONTAINER) return Promise.resolve();
  if (!cloudInitialization) {
    cloudInitialization = Promise.resolve(Taro.cloud.init({ env: CLOUD_ENV_ID }))
      .catch((error) => {
        cloudInitialization = null;
        throw error;
      });
  }
  return cloudInitialization;
}

async function request<T>({
  path,
  method = 'GET',
  data,
  header,
  responseType = 'text',
  timeout = 15000
}: RequestOptions): Promise<ApiResponse<T>> {
  if (USE_CLOUD_CONTAINER) {
    await initializeCloudContainer();
    const response = await Taro.cloud.callContainer<T, RequestData>({
      config: { env: CLOUD_ENV_ID },
      path,
      method,
      data,
      header: {
        ...header,
        'X-WX-SERVICE': CLOUD_SERVICE_NAME
      },
      responseType,
      timeout: Math.min(timeout, 15000)
    });
    return { statusCode: response.statusCode, data: response.data };
  }

  const response = await Taro.request<T>({
    url: apiUrl(path),
    method,
    data,
    header,
    responseType,
    timeout
  });
  return { statusCode: response.statusCode, data: response.data };
}

export async function requestJson<T>(options: RequestOptions, fallbackMessage: string): Promise<T> {
  const response = await request<T | ApiError>(options);
  if (response.statusCode >= 200 && response.statusCode < 300) {
    return response.data as T;
  }
  const message = typeof response.data === 'object' && response.data
    ? (response.data as ApiError).message
    : undefined;
  throw new Error(message || fallbackMessage);
}

export async function runAsyncTask<T>(
  path: string,
  data: RequestData | undefined,
  fallbackMessage: string,
  timeout = 120000,
  header: Record<string, string> = { 'content-type': 'application/json' }
): Promise<T> {
  const submission = await requestJson<TaskSubmission>({
    path,
    method: 'POST',
    data,
    header,
    timeout: 15000
  }, fallbackMessage);

  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    await delay(1000);
    const task = await requestJson<TaskSnapshot<T>>({
      path: `/api/tasks/${encodeURIComponent(submission.taskId)}`,
      timeout: 15000
    }, fallbackMessage);
    if (task.status === 'SUCCEEDED' && task.result !== undefined) {
      return task.result;
    }
    if (task.status === 'FAILED') {
      throw new Error(task.error || fallbackMessage);
    }
  }
  throw new Error('准备时间有点久，请稍后再试。');
}

export async function downloadAudioFile(path: string): Promise<DownloadedAudioFile> {
  if (!USE_CLOUD_CONTAINER) {
    const cacheBust = path.includes('?') ? '&' : '?';
    const download = await Taro.downloadFile({
      url: `${apiUrl(path)}${cacheBust}play=${Date.now()}`,
      timeout: 30000
    });
    if (download.statusCode < 200 || download.statusCode >= 300 || !download.tempFilePath) {
      throw new Error(`音频下载失败（HTTP ${download.statusCode}）`);
    }
    return { filePath: download.tempFilePath, cleanup: () => undefined };
  }

  const response = await request<ArrayBuffer>({
    path,
    responseType: 'arraybuffer',
    timeout: 15000
  });
  if (response.statusCode < 200 || response.statusCode >= 300 || !(response.data instanceof ArrayBuffer)) {
    throw new Error(`音频下载失败（HTTP ${response.statusCode}）`);
  }

  const filePath = `${Taro.env.USER_DATA_PATH}/yuyah-audio-${Date.now()}-${Math.random().toString(16).slice(2)}.mp3`;
  const fileSystem = Taro.getFileSystemManager();
  await new Promise<void>((resolve, reject) => {
    fileSystem.writeFile({
      filePath,
      data: response.data,
      success: () => resolve(),
      fail: (result) => reject(new Error(result.errMsg || '音频临时文件写入失败'))
    });
  });

  let cleaned = false;
  return {
    filePath,
    cleanup: () => {
      if (cleaned) return;
      cleaned = true;
      fileSystem.unlink({ filePath, fail: () => undefined });
    }
  };
}

export async function readLocalFile(filePath: string): Promise<ArrayBuffer> {
  const fileSystem = Taro.getFileSystemManager();
  return new Promise<ArrayBuffer>((resolve, reject) => {
    fileSystem.readFile({
      filePath,
      success: (result) => {
        if (result.data instanceof ArrayBuffer) {
          resolve(result.data);
        } else {
          reject(new Error('录音文件格式不正确'));
        }
      },
      fail: (result) => reject(new Error(result.errMsg || '录音文件读取失败'))
    });
  });
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}
