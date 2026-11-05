import { useEffect, useState } from 'react';
import { onSlowRequests } from '../api/client.js';
import { t } from '../i18n/index.js';
import styles from './WakeBanner.module.css';

/**
 * The API runs on a free plan that sleeps after an hour without visits. When a request is slow, this banner tells
 * the user the server is starting, instead of leaving them in front of a page that seems frozen.
 */
export default function WakeBanner() {
  const [slow, setSlow] = useState(false);
  useEffect(() => onSlowRequests(setSlow), []);
  return (
    <div className={styles.live} role="status" aria-live="polite">
      {slow && (
        <p className={styles.banner}>
          <span className={styles.spinner} aria-hidden="true" />
          {t('app.waking')}
        </p>
      )}
    </div>
  );
}
