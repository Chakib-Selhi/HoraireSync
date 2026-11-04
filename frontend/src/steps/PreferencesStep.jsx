import StepLayout from '../components/StepLayout.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { DAYS } from '../lib/format.js';
import { campusesOf } from '../lib/schedule.js';
import { useSchedule } from '../state/ScheduleContext.jsx';

const MODES = ['IN_PERSON', 'HYBRID', 'ONLINE'];

export default function PreferencesStep() {
  const { state, dispatch } = useSchedule();
  const prefs = state.preferences;
  const campuses = campusesOf(state.courses);
  const toggle = (key, value) => dispatch({ type: 'togglePreference', key, value });

  const checkboxes = (key, values, label) =>
    values.map((value) => (
      <label key={value} className={ui.choice}>
        <input type="checkbox" checked={prefs[key].includes(value)} onChange={() => toggle(key, value)} />
        {label(value)}
      </label>
    ));

  return (
    <StepLayout
      title={t('preferences.title')}
      hint={t('preferences.hint')}
      onBack={() => dispatch({ type: 'back' })}
      onNext={() => dispatch({ type: 'next' })}
    >
      <div className={ui.stack}>
        <fieldset className={ui.fieldset}>
          <legend className={ui.legend}>{t('preferences.timeOfDay')}</legend>
          {['ANY', 'DAY', 'EVENING'].map((value) => (
            <label key={value} className={ui.choice}>
              <input type="radio" name="timeOfDay" checked={prefs.timeOfDay === value}
                onChange={() => dispatch({ type: 'setPreference', key: 'timeOfDay', value })} />
              {t(`timeOfDay.${value}`)}
            </label>
          ))}
          <p className={ui.hint}>{t('preferences.timeOfDayHint')}</p>
        </fieldset>
        <fieldset className={ui.fieldset}>
          <legend className={ui.legend}>{t('preferences.modes')}</legend>
          {checkboxes('modes', MODES, (m) => t(`modes.${m}`))}
        </fieldset>
        <fieldset className={ui.fieldset}>
          <legend className={ui.legend}>{t('preferences.campuses')}</legend>
          {campuses.length === 0 ? <p className={ui.hint}>{t('preferences.noCampus')}</p> : checkboxes('campuses', campuses, (c) => c)}
        </fieldset>
        <fieldset className={ui.fieldset}>
          <legend className={ui.legend}>{t('preferences.freeDays')}</legend>
          {checkboxes('freeDays', DAYS.slice(0, 6), (d) => t(`days.${d}`))}
        </fieldset>
      </div>
    </StepLayout>
  );
}
