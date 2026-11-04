import { t } from '../i18n/index.js';
import { DAYS } from '../lib/format.js';
import ui from './ui.module.css';

const MODES = ['IN_PERSON', 'ONLINE', 'HYBRID', 'UNKNOWN'];
const TYPES = ['LECTURE', 'LAB', 'EXAM', 'OTHER'];

/** Last part of a path: "meetings[1].room" → "room". */
export const fieldName = (path) => path.split('.').pop();

/** Converts "09:30:00" to the "09:30" an <input type="time"> expects. */
const timeValue = (v) => (v ? v.slice(0, 5) : '');

/**
 * Input matching the kind of value: select for day, mode and type, time and date pickers,
 * text otherwise. Always labelled.
 */
export default function FieldInput({ id, name, label, value, onChange, required }) {
  const common = { id, required, 'aria-required': required || undefined };
  let control;
  if (name === 'day') {
    control = (
      <select {...common} value={value ?? ''} onChange={(e) => onChange(e.target.value || null)}>
        <option value="">-</option>
        {DAYS.map((d) => (
          <option key={d} value={d}>
            {t(`days.${d}`)}
          </option>
        ))}
      </select>
    );
  } else if (name === 'mode') {
    control = (
      <select {...common} value={value ?? 'UNKNOWN'} onChange={(e) => onChange(e.target.value === 'UNKNOWN' ? null : e.target.value)}>
        {MODES.map((m) => (
          <option key={m} value={m}>
            {t(`modes.${m}`)}
          </option>
        ))}
      </select>
    );
  } else if (name === 'type') {
    control = (
      <select {...common} value={value ?? 'LECTURE'} onChange={(e) => onChange(e.target.value)}>
        {TYPES.map((m) => (
          <option key={m} value={m}>
            {t(`meetingTypes.${m}`)}
          </option>
        ))}
      </select>
    );
  } else if (name === 'start' || name === 'end') {
    control = <input {...common} type="time" value={timeValue(value)} onChange={(e) => onChange(e.target.value || null)} />;
  } else if (['firstDate', 'lastDate', 'onlyDate', 'startDate', 'endDate', 'date'].includes(name)) {
    control = <input {...common} type="date" value={value ?? ''} onChange={(e) => onChange(e.target.value || null)} />;
  } else {
    control = <input {...common} type="text" value={value ?? ''} onChange={(e) => onChange(e.target.value)} />;
  }
  return (
    <div className={ui.field}>
      <label htmlFor={id}>
        {label}
        {required && <span className="visually-hidden"> ({t('common.required')})</span>}
      </label>
      {control}
    </div>
  );
}
