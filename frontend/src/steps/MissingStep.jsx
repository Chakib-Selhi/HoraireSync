import { useRef } from 'react';
import FieldInput, { fieldName } from '../components/FieldInput.jsx';
import MissingBadge from '../components/MissingBadge.jsx';
import StepLayout from '../components/StepLayout.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { describePath, groupNumber } from '../lib/format.js';
import { addMeeting, essentialProblems, getField, isEssentialPath, setField } from '../lib/schedule.js';
import { orderedSelections } from '../state/reducer.js';
import { useSchedule } from '../state/ScheduleContext.jsx';

const MEETING_PATH = /^meetings\[(\d+)\]\./;

/**
 * Paths to show for a section: the fields the source did not publish, plus the essential ones still
 * empty (for instance after adding a meeting). Fields stay listed once filled, so the user can correct them.
 */
export function pathsToShow(section, term) {
  const essential = essentialProblems([{ section }], term).map((p) => p.path);
  const paths = [...new Set([...section.missingFields, ...essential])].filter((p) => p !== 'meetings');
  return paths.sort((a, b) => a.localeCompare(b, 'en', { numeric: true }));
}

export default function MissingStep() {
  const { state, dispatch } = useSchedule();
  const selections = orderedSelections(state);
  // Sections keep their form once shown, even when every gap is filled, so the user can still correct values.
  const shown = useRef(new Set());
  selections
    .filter(({ section }) => pathsToShow(section, state.term).length > 0 || section.meetings.length === 0)
    .forEach(({ section }) => shown.current.add(section.courseCode));
  const withGaps = selections.filter(({ section }) => shown.current.has(section.courseCode));

  return (
    <StepLayout
      title={t('missing.title')}
      hint={t('missing.hint')}
      onBack={() => dispatch({ type: 'back' })}
      onNext={() => dispatch({ type: 'next' })}
    >
      {withGaps.length === 0 ? (
        <p className={ui.badgeSuccess}>{t('missing.nothing')}</p>
      ) : (
        <div className={ui.stack}>
          {withGaps.map(({ courseTitle, section }) => (
            <SectionGaps key={section.courseCode} courseTitle={courseTitle} section={section} term={state.term}
              onChange={(updated) => dispatch({ type: 'updateSelection', code: section.courseCode, section: updated })} />
          ))}
        </div>
      )}
    </StepLayout>
  );
}

function SectionGaps({ courseTitle, section, term, onChange }) {
  // A field leaves missingFields as soon as it has a value; it stays on screen so the user can correct it.
  const shown = useRef(new Set());
  pathsToShow(section, term).forEach((p) => shown.current.add(p));
  const paths = [...shown.current].sort((a, b) => a.localeCompare(b, 'en', { numeric: true }));
  const id = (path) => `${section.courseCode}-${path}`.replace(/[^\w-]/g, '-');
  return (
    <section className={`${ui.card} ${ui.stack}`} aria-labelledby={id('title')}>
      <h3 id={id('title')}>
        {section.courseCode} {courseTitle}
        {section.sectionNumber && ` · ${t('sections.group', { n: groupNumber(section.sectionNumber) })}`}
      </h3>
      {section.meetings.length === 0 && (
        <p>
          <MissingBadge /> {t('missing.noMeetings')}
        </p>
      )}
      {paths.map((path) => {
        const index = MEETING_PATH.exec(path)?.[1];
        const meeting = index != null ? section.meetings[Number(index)] : null;
        const essential = isEssentialPath(path, meeting, term);
        const value = getField(section, path);
        return (
          <div key={path} className={ui.row}>
            <div style={{ flex: '1 1 220px' }}>
              <FieldInput
                id={id(path)}
                name={fieldName(path)}
                label={describePath(section, path)}
                value={value}
                required={essential}
                onChange={(v) => onChange(setField(section, path, v))}
              />
            </div>
            <span className={essential ? ui.badgeDanger : ui.badgeInfo}>
              {essential ? t('missing.essential') : t('missing.optional')}
            </span>
            {value == null && <MissingBadge />}
          </div>
        );
      })}
      <button type="button" className={ui.secondary} onClick={() => onChange(addMeeting(section))}>
        {t('manual.addMeeting')}
      </button>
    </section>
  );
}
