const TRANSLATE_ENDPOINT = 'https://api.mymemory.translated.net/get';

function decodeEntities(text) {
  return String(text || '')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>');
}

function translateToEnglish(sourceText) {
  return new Promise(function (resolve, reject) {
    wx.request({
      url: TRANSLATE_ENDPOINT,
      method: 'GET',
      data: {
        q: sourceText,
        langpair: 'zh-CN|en'
      },
      timeout: 15000,
      success: function (response) {
        const data = response.data || {};
        const translatedText = data.responseData && data.responseData.translatedText;

        if (response.statusCode >= 200 && response.statusCode < 300 && translatedText) {
          resolve(decodeEntities(translatedText));
          return;
        }

        reject(new Error('翻译服务暂时没有回答，请稍后再试。'));
      },
      fail: function (error) {
        reject(new Error(error.errMsg || '网络连接失败，请检查网络后再试。'));
      }
    });
  });
}

module.exports = {
  TRANSLATE_ENDPOINT: TRANSLATE_ENDPOINT,
  translateToEnglish: translateToEnglish
};
