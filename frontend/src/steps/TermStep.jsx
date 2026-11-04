import { useState } from 'react';
import { api } from '../api/client.js';
import ErrorMessage from '../components/ErrorMessage.jsx';
import FieldInput from '../components/FieldInput.jsx';
import MissingBadge from '../components/MissingBadge.jsx';
import StepLayout from '../components/StepLayout.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { formatDate } from '../lib/format.js';
import { useAsync } from '../lib/useAsync.js';
import { useSchedule } from '../state/ScheduleContext.jsx';

const manualTerm = (changes) => ({
  id: 'session',
  label: '',
  startDate: null,
  endDate: null,
  excludedDates: [],
  readingWeekDates: [],
  readingWeekNote: null,
  missingFields: [],
  ...changes,
});

export default function TermStep() {
  const { state, dispatch } = useSchedule();
  const supported = !state.manual;
  const terms = useAsync(() => api.terms(state.school.id), [state.school?.id], { enabled: supported });
  const [holiday, setHoliday] = useState('');
  const term = state.term;

  const update = (changes) =>
    term ? dispatch({ type: 'updateTerm', changes }) : dispatch({ type: 'selectTerm', term: manualTerm(changes) });

  const addHoliday = () => {
    if (!holiday) return;
    update({ excludedDates: [...new Set([...(term?.excludedDates ?? []), holiday])].sort() });
    setHoliday('');
  };

  return (
    <StepLayout
      title={t('term.title')}
      hint={t('term.datesHint')}
      onBack={() => dispatch({ type: 'back' })}
      onNext={() => dispatch({ type: 'next' })}
      nextDisabled={!term || (state.manual && !term.label?.trim())}
    >
      <div className={ui.stack}>
        {supported && (
          <>
            {terms.loading && <p>{t('common.loading')}</p>}
            <ErrorMessage error={terms.error} />
            {terms.data && (
              <div className={ui.field}>
                <label htmlFor="term">{t('term.select')}</label>
                <select
                  id="term"
                  value={term?.id ?? ''}
                  onChange={(e) => {
                    const chosen = terms.data.find((x) => x.id === e.target.value);
                    if (chosen) dispatch({ type: 'selectTerm', term: chosen });
                  }}
                >
                  <option value="">-</option>
                  {terms.data.map((x) => (
                    <option key={x.id} value={x.id}>
                      {x.label}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </>
        )}
        {state.manual && (
          <FieldInput id="term-label" name="label" label={t('term.label')} value={term?.label ?? ''} required
            onChange={(label) => update({ label, id: label.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-') || 'session' })} />
        )}
        {(term || state.manual) && (
          <>
            <div className={ui.row}>
              {['startDate', 'endDate'].map((name) => (
                <div key={name}>
                  <FieldInput id={`term-${name}`} name={name} label={t(name === 'startDate' ? 'term.start' : 'term.end')}
                    value={term?.[name] ?? null} onChange={(value) => update({ [name]: value })} />
                  {supported && term?.missingFields?.includes(name) && !term?.[name] && (
                    <p className={ui.hint}>
                      <MissingBadge /> {t('term.missingDate')}
                    </p>
                  )}
                </div>
              ))}
            </div>
            <div>
              <h3>{t('term.holidays')}</h3>
              {(term?.excludedDates ?? []).length === 0 ? (
                <p className={ui.hint}>{t('common.none')}</p>
              ) : (
                <ul className={ui.list}>
                  {term.excludedDates.map((d) => (
                    <li key={d} className={ui.row}>
                      {formatDate(d)}
                      {state.manual && (
                        <button type="button" className={ui.link}
                          onClick={() => update({ excludedDates: term.excludedDates.filter((x) => x !== d) })}>
                          {t('common.remove')}
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
              )}
              {state.manual && (
                <div className={ui.row}>
                  <FieldInput id="holiday" name="date" label={t('term.holidayDate')} value={holiday} onChange={setHoliday} />
                  <button type="button" className={ui.secondary} onClick={addHoliday}>
                    {t('term.addHoliday')}
                  </button>
                </div>
              )}
              {term?.readingWeekDates?.length > 0 && (
                <p>
                  {t('term.readingWeek', {
                    from: formatDate(term.readingWeekDates[0]),
                    to: formatDate(term.readingWeekDates[term.readingWeekDates.length - 1]),
                  })}{' '}
                  {term.readingWeekNote && t('term.readingWeekNote', { note: term.readingWeekNote })}
                </p>
              )}
            </div>
          </>
        )}
      </div>
    </StepLayout>
  );
}
