const dateUtils = require('../../utils/date');

const TASKS_KEY = 'kidsPlanTasks';
const COURSES_KEY = 'kidsPlanCourses';

function iconForCategory(category) {
  const icons = {
    英语: 'A',
    阅读: '书',
    运动: '跑',
    艺术: '画',
    其他: '星'
  };
  return icons[category] || icons.其他;
}

function classForCategory(category) {
  const classes = {
    英语: 'english',
    阅读: 'reading',
    运动: 'sports',
    艺术: 'art',
    其他: 'other'
  };
  return classes[category] || classes.其他;
}

function seedTasks(dateKey) {
  return [
    { id: Date.now() + 1, title: '听一首英文儿歌', category: '英语', minutes: 10, done: false, icon: 'A' },
    { id: Date.now() + 2, title: '学会 3 个新单词', category: '英语', minutes: 15, done: false, icon: 'A' },
    { id: Date.now() + 3, title: '亲子共读 10 分钟', category: '阅读', minutes: 10, done: false, icon: '书' }
  ].map(function (task) {
    return Object.assign(task, { dateKey: dateKey });
  });
}

function defaultCourses() {
  return [
    { weekday: '周一', start: '17:00', title: '英语故事时间', duration: '20 分钟', color: 'coral' },
    { weekday: '周二', start: '16:30', title: '亲子共读', duration: '15 分钟', color: 'mint' },
    { weekday: '周三', start: '17:00', title: '英文儿歌', duration: '20 分钟', color: 'yellow' },
    { weekday: '周四', start: '16:30', title: '自然小观察', duration: '25 分钟', color: 'blue' },
    { weekday: '周五', start: '17:00', title: '一周英文复习', duration: '20 分钟', color: 'purple' },
    { weekday: '周六', start: '10:00', title: '自由创作时间', duration: '30 分钟', color: 'coral' },
    { weekday: '周日', start: '10:00', title: '家庭分享会', duration: '20 分钟', color: 'mint' }
  ];
}

function dateFromKey(key) {
  const parts = key.split('-').map(Number);
  return new Date(parts[0], parts[1] - 1, parts[2]);
}

Page({
  data: {
    activeView: 'today',
    weekDates: [],
    selectedDate: '',
    selectedDateLabel: '',
    tasks: [],
    courses: [],
    categories: ['英语', '阅读', '运动', '艺术', '其他'],
    showComposer: false,
    draft: { title: '', category: '英语', minutes: '15' },
    totalMinutes: 0,
    doneCount: 0,
    progressPercent: 0,
    recordDays: [],
    totalDone: 0
  },

  onLoad: function () {
    const todayKey = dateUtils.toDateKey(new Date());
    const weekDates = dateUtils.getWeekDates(new Date());
    const taskMap = wx.getStorageSync(TASKS_KEY) || {};
    if (!taskMap[todayKey]) {
      taskMap[todayKey] = seedTasks(todayKey);
      wx.setStorageSync(TASKS_KEY, taskMap);
    }
    const courses = wx.getStorageSync(COURSES_KEY) || defaultCourses();
    wx.setStorageSync(COURSES_KEY, courses);
    this.setData({ weekDates: weekDates, selectedDate: todayKey, courses: courses });
    this.refreshView();
  },

  switchView: function (event) {
    this.setData({ activeView: event.currentTarget.dataset.view });
    this.refreshView();
  },

  selectDate: function (event) {
    this.setData({ selectedDate: event.currentTarget.dataset.date });
    this.refreshView();
  },

  refreshView: function () {
    const taskMap = wx.getStorageSync(TASKS_KEY) || {};
    const tasks = (taskMap[this.data.selectedDate] || []).map(function (task) {
      return Object.assign({}, task, {
        categoryClass: classForCategory(task.category),
        icon: task.icon || iconForCategory(task.category)
      });
    });
    const doneCount = tasks.filter(function (task) { return task.done; }).length;
    const totalMinutes = tasks.reduce(function (sum, task) { return sum + Number(task.minutes || 0); }, 0);
    const progressPercent = tasks.length ? Math.round(doneCount / tasks.length * 100) : 0;
    const records = Object.keys(taskMap).map(function (key) {
      const dayTasks = taskMap[key] || [];
      return {
        key: key,
        label: dateUtils.formatShortDate(dateFromKey(key)),
        done: dayTasks.filter(function (task) { return task.done; }).length,
        total: dayTasks.length,
        percent: dayTasks.length ? Math.round(dayTasks.filter(function (task) { return task.done; }).length / dayTasks.length * 100) : 0
      };
    }).sort(function (left, right) { return right.key.localeCompare(left.key); }).slice(0, 14);
    const totalDone = records.reduce(function (sum, day) { return sum + day.done; }, 0);
    const selectedDate = dateFromKey(this.data.selectedDate);
    this.setData({
      tasks: tasks,
      selectedDateLabel: dateUtils.formatShortDate(selectedDate),
      doneCount: doneCount,
      totalMinutes: totalMinutes,
      progressPercent: progressPercent,
      recordDays: records,
      totalDone: totalDone
    });
  },

  openComposer: function () {
    this.setData({ showComposer: true, draft: { title: '', category: '英语', minutes: '15' } });
  },

  closeComposer: function () {
    this.setData({ showComposer: false });
  },

  noop: function () {},

  updateTitle: function (event) {
    this.setData({ 'draft.title': event.detail.value });
  },

  updateMinutes: function (event) {
    this.setData({ 'draft.minutes': event.detail.value.replace(/\D/g, '').slice(0, 3) });
  },

  chooseCategory: function (event) {
    this.setData({ 'draft.category': this.data.categories[event.detail.value] });
  },

  addTask: function () {
    const title = this.data.draft.title.trim();
    if (!title) {
      wx.showToast({ title: '写下今天要做的事', icon: 'none' });
      return;
    }
    const taskMap = wx.getStorageSync(TASKS_KEY) || {};
    const task = {
      id: Date.now(),
      title: title,
      category: this.data.draft.category,
      minutes: Number(this.data.draft.minutes) || 15,
      done: false,
      icon: iconForCategory(this.data.draft.category),
      categoryClass: classForCategory(this.data.draft.category),
      dateKey: this.data.selectedDate
    };
    taskMap[this.data.selectedDate] = (taskMap[this.data.selectedDate] || []).concat(task);
    wx.setStorageSync(TASKS_KEY, taskMap);
    this.setData({ showComposer: false });
    this.refreshView();
    wx.showToast({ title: '计划加好啦', icon: 'success' });
  },

  toggleTask: function (event) {
    const taskId = Number(event.currentTarget.dataset.id);
    const taskMap = wx.getStorageSync(TASKS_KEY) || {};
    const tasks = (taskMap[this.data.selectedDate] || []).map(function (task) {
      if (task.id === taskId) {
        return Object.assign({}, task, { done: !task.done });
      }
      return task;
    });
    taskMap[this.data.selectedDate] = tasks;
    wx.setStorageSync(TASKS_KEY, taskMap);
    this.refreshView();
  },

  deleteTask: function (event) {
    const taskId = Number(event.currentTarget.dataset.id);
    const taskMap = wx.getStorageSync(TASKS_KEY) || {};
    taskMap[this.data.selectedDate] = (taskMap[this.data.selectedDate] || []).filter(function (task) { return task.id !== taskId; });
    wx.setStorageSync(TASKS_KEY, taskMap);
    this.refreshView();
  },

  generateCourses: function () {
    const page = this;
    wx.showModal({
      title: '生成一周课程表？',
      content: '会安排英语、阅读、观察和创作时间。',
      confirmText: '生成',
      confirmColor: '#e56f62',
      success: function (result) {
        if (result.confirm) {
          const courses = defaultCourses();
          wx.setStorageSync(COURSES_KEY, courses);
          page.setData({ courses: courses });
          wx.showToast({ title: '课程表准备好了', icon: 'success' });
        }
      }
    });
  }
});
