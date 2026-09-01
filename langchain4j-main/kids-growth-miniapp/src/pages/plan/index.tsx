import { useMemo, useState } from 'react';
import { Button, Image, Text, View } from '@tarojs/components';
import Taro, { useDidShow } from '@tarojs/taro';
import planEmpty from '../../assets/plan-empty.png';
import './index.scss';

type ViewMode = 'today' | 'courses' | 'records';

interface Task {
  id: number;
  title: string;
  minutes: number;
  done: boolean;
}

const TASKS_KEY = 'kidsPlanTasksTaro';
const COURSES = [
  ['周一', '17:00', '英语故事时间'],
  ['周二', '16:30', '亲子共读'],
  ['周三', '17:00', '英文儿歌'],
  ['周四', '16:30', '自然小观察'],
  ['周五', '17:00', '一周英文复习'],
  ['周六', '10:00', '自由创作时间'],
  ['周日', '10:00', '家庭分享会']
];

function todayKey() {
  const date = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function seedTasks(): Task[] {
  return [
    { id: Date.now() + 1, title: '听一首英文儿歌', minutes: 10, done: false },
    { id: Date.now() + 2, title: '学会 3 个新单词', minutes: 15, done: false },
    { id: Date.now() + 3, title: '亲子共读 10 分钟', minutes: 10, done: false }
  ];
}

export default function PlanPage() {
  const [activeView, setActiveView] = useState<ViewMode>('today');
  const [taskMap, setTaskMap] = useState<Record<string, Task[]>>({});
  const key = todayKey();
  const tasks = taskMap[key] || [];
  const doneCount = tasks.filter((task) => task.done).length;
  const progress = tasks.length ? Math.round(doneCount / tasks.length * 100) : 0;

  useDidShow(() => {
    const stored = Taro.getStorageSync(TASKS_KEY) as Record<string, Task[]> | undefined;
    const next = stored && Object.keys(stored).length ? stored : { [key]: seedTasks() };
    if (!stored || !Object.keys(stored).length) Taro.setStorageSync(TASKS_KEY, next);
    setTaskMap(next);
  });

  const recordDays = useMemo(() => Object.entries(taskMap)
    .map(([date, dayTasks]) => ({
      date,
      done: dayTasks.filter((task) => task.done).length,
      total: dayTasks.length
    }))
    .sort((left, right) => right.date.localeCompare(left.date))
    .slice(0, 14), [taskMap]);

  const saveTasks = (nextTasks: Task[]) => {
    const nextMap = { ...taskMap, [key]: nextTasks };
    setTaskMap(nextMap);
    Taro.setStorageSync(TASKS_KEY, nextMap);
  };

  const addTask = async () => {
    const result = await Taro.showModal({
      title: '加一件小事',
      content: '写下今天想完成的事',
      editable: true,
      placeholderText: '例如：读三遍 apple',
      confirmText: '加到计划'
    } as never) as { confirm: boolean; content?: string };
    const title = result.content?.trim();
    if (!result.confirm || !title) return;
    saveTasks(tasks.concat({ id: Date.now(), title, minutes: 15, done: false }));
  };

  const toggleTask = (id: number) => {
    saveTasks(tasks.map((task) => task.id === id ? { ...task, done: !task.done } : task));
  };

  const deleteTask = (id: number) => {
    saveTasks(tasks.filter((task) => task.id !== id));
  };

  return (
    <View className='page-shell plan-page'>
      <Text className='eyebrow'>GROW A LITTLE EVERY DAY</Text>
      <Text className='page-title'>我的计划</Text>
      <Text className='page-subtitle'>把每天的小目标，变成看得见的成长。</Text>

      <View className='view-tabs'>
        {([
          ['today', '今日计划'],
          ['courses', '课程表'],
          ['records', '成长记录']
        ] as [ViewMode, string][]).map(([value, label]) => (
          <Button key={value} className={`view-tab ${activeView === value ? 'active' : ''}`} onClick={() => setActiveView(value)}>
            {label}
          </Button>
        ))}
      </View>

      {activeView === 'today' && (
        <View>
          <View className='progress-panel'>
            <Text className='progress-label'>今天的小步子</Text>
            <Text className='progress-number'>{doneCount} / {tasks.length}</Text>
            <View className='progress-track'><View className='progress-fill' style={{ width: `${progress}%` }} /></View>
            <Text className='progress-copy'>{progress === 100 ? '今天的星星都收集齐啦！' : '完成一个，就离目标更近一点。'}</Text>
          </View>

          <View className='task-heading'>
            <Text className='task-section-title'>今天要做</Text>
            <Button className='add-button' onClick={addTask}>+</Button>
          </View>

          {tasks.length ? (
            <View className='task-list'>
              {tasks.map((task) => (
                <View key={task.id} className={`task-item ${task.done ? 'done' : ''}`}>
                  <Button className='check-button' onClick={() => toggleTask(task.id)}>{task.done ? '✓' : ''}</Button>
                  <View className='task-copy' onClick={() => toggleTask(task.id)}>
                    <Text className='task-title'>{task.title}</Text>
                    <Text className='task-meta'>预计 {task.minutes} 分钟</Text>
                  </View>
                  <Button className='delete-button' onClick={() => deleteTask(task.id)}>×</Button>
                </View>
              ))}
            </View>
          ) : (
            <View className='panel empty-plan'>
              <Image className='empty-image' src={planEmpty} mode='aspectFit' />
              <Text className='empty-title'>今天还没有小计划</Text>
            </View>
          )}
        </View>
      )}

      {activeView === 'courses' && (
        <View className='course-list'>
          {COURSES.map(([day, time, title]) => (
            <View key={day} className='course-item'>
              <Text className='course-day'>{day}</Text>
              <View className='course-card'>
                <Text className='course-time'>{time}</Text>
                <Text className='course-title'>{title}</Text>
                <Text className='course-duration'>20 分钟</Text>
              </View>
            </View>
          ))}
        </View>
      )}

      {activeView === 'records' && (
        <View>
          <View className='panel record-summary'>
            <Text className='record-kicker'>累计完成</Text>
            <Text className='record-number'>{recordDays.reduce((sum, day) => sum + day.done, 0)}</Text>
            <Text className='record-copy'>每一次勾选，都是成长留下的小脚印。</Text>
          </View>
          <View className='record-list'>
            {recordDays.map((day) => (
              <View key={day.date} className='record-item'>
                <Text className='record-date'>{day.date}</Text>
                <Text className='record-count'>{day.done} / {day.total} 件</Text>
              </View>
            ))}
          </View>
        </View>
      )}
    </View>
  );
}
