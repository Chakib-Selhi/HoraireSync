import { t } from '../i18n/index.js';

export const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** "09:30:00" or "09:30" → "9 h 30" (Quebec style). */
export function formatTime(value) {
  if (!value) return '';
  const [h, m] = value.split(':');
  return `${Number(h)} h ${m}`;
}

/** "09:30:00" → minutes since midnight. */
export function toMinutes(value) {
  if (!value) return null;
  const [h, m] = value.split(':').map(Number);
  return h * 60 + m;
}

/** "2027-03-01" → "1 mars 2027", read as a local date (no time zone shift). */
export function formatDate(iso) {
  if (!iso) return '';
  const [y, m, d] = iso.split('-').map(Number);
  return new Intl.DateTimeFormat('fr-CA', { day: 'numeric', month: 'long', year: 'numeric' }).format(
    new Date(y, m - 1, d),
  );
}

export const dayLabel = (day) => (day ? t(`days.${day}`) : t('common.notAvailable'));

/** Section number without leading zeros, as in the exported titles: "020" → "20". */
export const groupNumber = (n) => (n ? n.replace(/^0+(?=.)/, '') : '');
