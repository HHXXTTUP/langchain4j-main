Page({
  data: {
    models: ['童话绘本', '软萌黏土', '彩色蜡笔'],
    modelIndex: 0,
    prompt: '',
    styles: ['温柔日常', '奇幻冒险', '自然探索'],
    activeStyle: '温柔日常'
  },

  chooseModel: function (event) {
    this.setData({ modelIndex: Number(event.detail.value) });
  },

  handlePromptInput: function (event) {
    this.setData({ prompt: event.detail.value });
  },

  chooseStyle: function (event) {
    this.setData({ activeStyle: event.currentTarget.dataset.style });
  }
});
