export default defineAppConfig({
  // Let WeChat inject only the native components used by each page.
  lazyCodeLoading: 'requiredComponents',
  pages: [
    'pages/learning/index',
    'pages/animation/index',
    'pages/plan/index'
  ],
  window: {
    navigationBarTitleText: '语芽学习屋',
    navigationBarBackgroundColor: '#fff9f3',
    navigationBarTextStyle: 'black',
    backgroundColor: '#fff9f3',
    backgroundTextStyle: 'light'
  },
  tabBar: {
    color: '#756f6a',
    selectedColor: '#e56f62',
    backgroundColor: '#fffdf9',
    borderStyle: 'white',
    list: [
      {
        pagePath: 'pages/learning/index',
        text: '学英语',
        iconPath: 'assets/tab-english.png',
        selectedIconPath: 'assets/tab-english-active.png'
      },
      {
        pagePath: 'pages/animation/index',
        text: '动画屋',
        iconPath: 'assets/tab-animation.png',
        selectedIconPath: 'assets/tab-animation-active.png'
      },
      {
        pagePath: 'pages/plan/index',
        text: '我的计划',
        iconPath: 'assets/tab-plan.png',
        selectedIconPath: 'assets/tab-plan-active.png'
      }
    ]
  }
});
