const translationService = require('../../services/translation');
const dateUtils = require('../../utils/date');

const HISTORY_KEY = 'englishTranslationHistory';

Page({
  data: {
    sourceText: '',
    translatedText: '',
    translating: false,
    quickPrompts: ['早上好', '我喜欢读故事', '今天真开心', '我们一起玩吧'],
    history: []
  },

  onShow: function () {
    this.setData({ history: wx.getStorageSync(HISTORY_KEY) || [] });
  },

  handleInput: function (event) {
    this.setData({ sourceText: event.detail.value });
  },

  useQuickPrompt: function (event) {
    this.setData({ sourceText: event.currentTarget.dataset.text });
  },

  translate: function () {
    const page = this;
    const sourceText = this.data.sourceText.trim();
    if (!sourceText) {
      wx.showToast({ title: '先写一句中文吧', icon: 'none' });
      return;
    }

    this.setData({ translating: true, translatedText: '' });
    translationService.translateToEnglish(sourceText)
      .then(function (translatedText) {
        const record = {
          id: Date.now(),
          source: sourceText,
          translation: translatedText,
          createdAt: dateUtils.formatDateTime(new Date())
        };
        const history = [record].concat(page.data.history).slice(0, 8);
        wx.setStorageSync(HISTORY_KEY, history);
        page.setData({ translatedText: translatedText, history: history });
      })
      .catch(function (error) {
        wx.showModal({
          title: '暂时没翻译出来',
          content: error.message,
          showCancel: false,
          confirmText: '知道啦'
        });
      })
      .finally(function () {
        page.setData({ translating: false });
      });
  },

  copyTranslation: function () {
    if (!this.data.translatedText) return;
    wx.setClipboardData({
      data: this.data.translatedText,
      success: function () {
        wx.showToast({ title: '英文已复制', icon: 'success' });
      }
    });
  },

  reuseHistory: function (event) {
    const item = this.data.history.find(function (record) {
      return String(record.id) === String(event.currentTarget.dataset.id);
    });
    if (item) {
      this.setData({ sourceText: item.source, translatedText: item.translation });
      wx.pageScrollTo({ scrollTop: 0, duration: 250 });
    }
  },

  clearHistory: function () {
    const page = this;
    wx.showModal({
      title: '清空翻译记录？',
      content: '清空后就不能找回啦。',
      confirmText: '清空',
      confirmColor: '#e56f62',
      success: function (result) {
        if (result.confirm) {
          wx.removeStorageSync(HISTORY_KEY);
          page.setData({ history: [] });
        }
      }
    });
  }
});
