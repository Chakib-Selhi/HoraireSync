import { useState } from 'react';
import { api } from '../api/client.js';
import ErrorMessage from '../components/ErrorMessage.jsx';
import StepLayout from '../components/StepLayout.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { useAsync } from '../lib/useAsync.js';
import { useSchedule } from '../state/ScheduleContext.jsx';

const OTHER = '__other__';

export default function SchoolStep() {
  const { state, dispatch } = useSchedule();
  const schools = useAsync(() => api.schools(), []);
  const [otherName, setOtherName] = useState(state.school?.id === 'autre' ? state.school.name : '');
  const selectedId = state.school?.id === 'autre' ? OTHER : state.school?.id;

  const choose = (school) => dispatch({ type: 'selectSchool', school });
  const chooseOther = (name) => {
    setOtherName(name);
    choose({ id: 'autre', name: name.trim() || t('school.other'), supported: false });
  };

  return (
    <StepLayout title={t('school.title')} onNext={() => dispatch({ type: 'next' })} nextDisabled={!state.school}>
      {schools.loading && <p>{t('common.loading')}</p>}
      <ErrorMessage error={schools.error} />
      <fieldset className={ui.fieldset}>
        <legend className="visually-hidden">{t('school.title')}</legend>
        {(schools.data ?? []).map((school) => (
          <label key={school.id} className={ui.choice}>
            <input type="radio" name="school" value={school.id} checked={selectedId === school.id}
              onChange={() => choose(school)} />
            <span>
              {school.name}{' '}
              <span className={school.supported ? ui.badgeSuccess : ui.badgeInfo}>
                {school.supported ? t('school.supported') : t('school.manual')}
              </span>
            </span>
          </label>
        ))}
        <label className={ui.choice}>
          <input type="radio" name="school" value={OTHER} checked={selectedId === OTHER}
            onChange={() => chooseOther(otherName)} />
          <span>
            {t('school.other')} <span className={ui.badgeInfo}>{t('school.manual')}</span>
          </span>
        </label>
        {selectedId === OTHER && (
          <div className={ui.field}>
            <label htmlFor="other-school">{t('school.otherName')}</label>
            <input id="other-school" type="text" value={otherName} onChange={(e) => chooseOther(e.target.value)} />
          </div>
        )}
      </fieldset>
      {state.manual && <p className={ui.hint}>{t('school.manualHint')}</p>}
    </StepLayout>
  );
}
