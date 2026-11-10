import { useEffect } from 'react';
import { warmUp } from './api/client.js';
import Stepper from './components/Stepper.jsx';
import WakeBanner from './components/WakeBanner.jsx';
import { t } from './i18n/index.js';
import { STEPS } from './state/reducer.js';
import { useSchedule } from './state/ScheduleContext.jsx';
import { useCourseLoader } from './state/useCourseLoader.js';
import CoursesStep from './steps/CoursesStep.jsx';
import ExportStep from './steps/ExportStep.jsx';
import PreferencesStep from './steps/PreferencesStep.jsx';
import ProgramStep from './steps/ProgramStep.jsx';
import ReviewStep from './steps/ReviewStep.jsx';
import SectionsStep from './steps/SectionsStep.jsx';
import TermStep from './steps/TermStep.jsx';
import styles from './App.module.css';

const SCREENS = {
  term: TermStep,
  program: ProgramStep,
  courses: CoursesStep,
  preferences: PreferencesStep,
  sections: SectionsStep,
  review: ReviewStep,
  export: ExportStep,
};

/** Steps whose content needs the full width (list and week view side by side). */
const WIDE = new Set(['sections', 'review']);

export default function App() {
  const { state, dispatch } = useSchedule();
  useCourseLoader(state, dispatch);
  useEffect(() => {
    warmUp();
  }, []);
  const name = STEPS[state.step];
  const Screen = SCREENS[name];
  return (
    <>
      <a href="#main" className={styles.skip}>
        {t('app.skipToContent')}
      </a>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <h1 className={styles.brand}>{t('app.title')}</h1>
          <Stepper state={state} onGoTo={(step) => dispatch({ type: 'goTo', step })} />
        </div>
      </header>
      <WakeBanner />
      <main id="main" className={WIDE.has(name) ? styles.wide : styles.narrow}>
        {/* key: each step is a fresh screen, so its heading receives focus */}
        <Screen key={state.step} />
      </main>
      <footer className={`${WIDE.has(name) ? styles.wide : styles.narrow} ${styles.footer}`}>
        <p>{t('app.disclaimer')}</p>
      </footer>
    </>
  );
}
