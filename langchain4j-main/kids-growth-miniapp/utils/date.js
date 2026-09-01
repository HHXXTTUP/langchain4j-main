const WEEKDAY_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

function pad(number) {
  return number < 10 ? '0' + number : String(number);
}

function toDateKey(date) {
  const value = date || new Date();
  return value.getFullYear() + '-' + pad(value.getMonth() + 1) + '-' + pad(value.getDate());
}

function formatShortDate(date) {
  const value = date || new Date();
  return (value.getMonth() + 1) + '月' + value.getDate() + '日';
}

function formatDateTime(date) {
  const value = date || new Date();
  return pad(value.getMonth() + 1) + '-' + pad(value.getDate()) + ' ' + pad(value.getHours()) + ':' + pad(value.getMinutes());
}

function getWeekDates(referenceDate) {
  const source = referenceDate || new Date();
  const mondayOffset = source.getDay() === 0 ? -6 : 1 - source.getDay();
  const monday = new Date(source.getFullYear(), source.getMonth(), source.getDate() + mondayOffset);

  return Array.from({ length: 7 }, function (_, index) {
    const date = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + index);
    return {
      key: toDateKey(date),
      weekday: WEEKDAY_NAMES[date.getDay()].replace('周', ''),
      day: date.getDate(),
      isToday: toDateKey(date) === toDateKey(new Date())
    };
  });
}

module.exports = {
  WEEKDAY_NAMES: WEEKDAY_NAMES,
  toDateKey: toDateKey,
  formatShortDate: formatShortDate,
  formatDateTime: formatDateTime,
  getWeekDates: getWeekDates
};
