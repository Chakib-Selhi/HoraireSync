import { useState } from 'react';
import { t } from '../i18n/index.js';
import { emptyMeeting, manualSection } from '../lib/schedule.js';
import FieldInput from './FieldInput.jsx';
import MeetingEditor from './MeetingEditor.jsx';
import ui from './ui.module.css';

/**
 * Form to type a course and its meetings. Used for schools without adapter, and for a course whose
 * schedule is not published. `fixedCode` locks the code when the course already exists.
 */
export default function ManualCourseForm({ idPrefix = 'manual', fixedCode, fixedTitle, onSubmit }) {
  const [code, setCode] = useState(fixedCode ?? '');
  const [title, setTitle] = useState(fixedTitle ?? '');
  const [sectionNumber, setSectionNumber] = useState('');
  const [instructor, setInstructor] = useState('');
  const [mode, setMode] = useState(null);
  const [meetings, setMeetings] = useState([emptyMeeting()]);
  const [error, setError] = useState(null);

  const submit = (event) => {
    event.preventDefault();
    if (!code.trim()) {
      setError(t('manual.codeRequired'));
      return;
    }
    onSubmit(manualSection({ courseCode: code, sectionNumber, instructor, mode, meetings }), title.trim() || null);
    if (!fixedCode) {
      setCode('');
      setTitle('');
    }
    setSectionNumber('');
    setInstructor('');
    setMode(null);
    setMeetings([emptyMeeting()]);
    setError(null);
  };

  return (
    <form className={`${ui.card} ${ui.stack}`} onSubmit={submit} noValidate>
      <h3>{t('manual.title')}</h3>
      <div className={ui.row}>
        <FieldInput id={`${idPrefix}-code`} name="code" label={t('manual.code')} value={code}
          onChange={setCode} required />
        <FieldInput id={`${idPrefix}-title`} name="title" label={t('manual.courseTitle')} value={title}
          onChange={setTitle} />
        <FieldInput id={`${idPrefix}-section`} name="section" label={t('manual.section')} value={sectionNumber}
          onChange={setSectionNumber} />
        <FieldInput id={`${idPrefix}-instructor`} name="instructor" label={t('manual.instructor')} value={instructor}
          onChange={setInstructor} />
        <FieldInput id={`${idPrefix}-mode`} name="mode" label={t('manual.mode')} value={mode} onChange={setMode} />
      </div>
      {error && <p className={ui.error} role="alert">{error}</p>}
      {meetings.map((meeting, index) => (
        <MeetingEditor
          key={index}
          idPrefix={idPrefix}
          index={index}
          meeting={meeting}
          onChange={(m) => setMeetings(meetings.map((x, i) => (i === index ? m : x)))}
          onRemove={meetings.length > 1 ? () => setMeetings(meetings.filter((_, i) => i !== index)) : null}
        />
      ))}
      <div className={ui.row}>
        <button type="button" className={ui.secondary} onClick={() => setMeetings([...meetings, emptyMeeting()])}>
          {t('manual.addMeeting')}
        </button>
        <button type="submit" className={ui.button}>
          {t('manual.save')}
        </button>
      </div>
    </form>
  );
}
