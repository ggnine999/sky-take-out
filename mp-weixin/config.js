// Fill in your deployed HTTPS API origin (including /user routing on the reverse proxy).
// Example format: https://your-host; no trailing /user is needed.
var apiBaseUrl = '';

function validateApiBaseUrl(value, isDevtools) {
  var url = (value || '').replace(/\/+$/, '');
  if (!/^https:\/\/[^/]+/.test(url) && !(isDevtools && /^http:\/\/[^/]+/.test(url))) {
    throw new Error('请在 mp-weixin/config.js 配置 HTTPS apiBaseUrl，并在微信后台登记 request 合法域名');
  }
  if (!isDevtools && /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:|\/|$)/i.test(url)) {
    throw new Error('真机不能使用 localhost，请配置可访问的服务器域名');
  }
  return url;
}

exports.validateApiBaseUrl = validateApiBaseUrl;
exports.getApiBaseUrl = function () {
  return validateApiBaseUrl(apiBaseUrl, wx.getSystemInfoSync().platform === 'devtools');
};
