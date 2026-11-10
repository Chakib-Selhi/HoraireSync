import { t } from '../i18n/index.js';

export const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** "09:30:00" → "9 h 30", "18:00" → "18 h" (Quebec style). */
export function formatTime(value) {
  if (!value) return '';
  const [h, m] = value.split(':');
  return m === '00' ? `${Number(h)} h` : `${Number(h)} h ${m}`;
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

/** "2027-01-11" → "11 janvier", for ranges inside one term. */
export function formatDayMonth(iso) {
  if (!iso) return '';
  const [y, m, d] = iso.split('-').map(Number);
  return new Intl.DateTimeFormat('fr-CA', { day: 'numeric', month: 'long' }).format(new Date(y, m - 1, d));
}

/** 300 → "5 heures", 270 → "4,5 heures", 60 → "1 heure". */
export function formatHours(minutes) {
  const n = Math.round((minutes / 60) * 10) / 10;
  return n === 1 ? t('week.hour', { n: 1 }) : t('week.hoursN', { n: String(n).replace('.', ',') });
}

/** ["Lundi", "Mercredi", "Vendredi"] → "Lundi, mercredi et vendredi". */
export function joinDays(days) {
  const names = days.map((d, i) => (i === 0 ? t(`days.${d}`) : t(`days.${d}`).toLowerCase()));
  return names.length <= 1 ? names.join('') : `${names.slice(0, -1).join(', ')} ${t('week.and')} ${names.at(-1)}`;
}

export const dayLabel = (day) => (day ? t(`days.${day}`) : t('common.notAvailable'));

/** Section number without leading zeros, as in the exported titles: "020" → "20". */
export const groupNumber = (n) => (n ? n.replace(/^0+(?=.)/, '') : '');

/** "INF1120 gr. 20", or just the code when the user typed no section number. */
export const sectionLabel = (courseCode, sectionNumber) =>
  sectionNumber ? `${courseCode} gr. ${groupNumber(sectionNumber)}` : courseCode;

/** Short description of a meeting for labels: "Cours, Lundi, 18 h 00". */
export function describeMeeting(meeting) {
  if (!meeting) return '';
  const parts = [t(`meetingTypes.${meeting.type ?? 'OTHER'}`)];
  if (meeting.onlyDate) parts.push(formatDate(meeting.onlyDate));
  else if (meeting.day) parts.push(t(`days.${meeting.day}`));
  if (meeting.start) parts.push(formatTime(meeting.start));
  return parts.join(', ');
}

/** Label of a missing-field path: "Local (Cours, Lundi, 18 h 00)". */
export function describePath(section, path) {
  const field = t(`fields.${path.split('.').pop()}`);
  const index = /^meetings\[(\d+)\]/.exec(path)?.[1];
  return index == null ? field : `${field} (${describeMeeting(section.meetings[Number(index)])})`;
}
