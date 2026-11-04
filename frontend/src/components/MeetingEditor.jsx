import { t } from '../i18n/index.js';
import FieldInput from './FieldInput.jsx';
import ui from './ui.module.css';
import styles from './MeetingEditor.module.css';

const FIELDS = ['type', 'day', 'start', 'end', 'room', 'campus'];

/** Every field of one meeting, used in manual mode. */
export default function MeetingEditor({ idPrefix, index, meeting, onChange, onRemove }) {
  return (
    <fieldset className={ui.fieldset}>
      <legend className={ui.legend}>{t('manual.meetingN', { n: index + 1 })}</legend>
      <div className={styles.grid}>
        {FIELDS.map((name) => (
          <FieldInput
            key={name}
            id={`${idPrefix}-${index}-${name}`}
            name={name}
            label={t(`meeting.${name}`)}
            value={meeting[name]}
            required={['day', 'start', 'end'].includes(name)}
            onChange={(value) => onChange({ ...meeting, [name]: value })}
          />
        ))}
        {meeting.type === 'EXAM' && (
          <FieldInput
            id={`${idPrefix}-${index}-onlyDate`}
            name="onlyDate"
            label={t('meeting.onlyDate')}
            value={meeting.onlyDate}
            required
            onChange={(value) => {
              const day = value ? dayOf(value) : meeting.day;
              onChange({ ...meeting, onlyDate: value, firstDate: value, lastDate: value, day });
            }}
          />
        )}
      </div>
      {onRemove && (
        <button type="button" className={ui.link} onClick={onRemove}>
          {t('manual.removeMeeting')}
        </button>
      )}
    </fieldset>
  );
}

/** "2027-02-27" → "SATURDAY", computed on the local calendar date. */
export function dayOf(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][new Date(y, m - 1, d).getDay()];
}
