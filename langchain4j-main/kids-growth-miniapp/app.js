App({
  globalData: {
    appName: '暖芽乐园'
  },

  onLaunch() {
    const storageVersion = wx.getStorageSync('storageVersion');
    if (!storageVersion) {
      wx.setStorageSync('storageVersion', 1);
    }
  }
});
