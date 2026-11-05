/**
 * Thin wrapper over fetch for the HoraireSync API.
 * Every failure becomes an ApiError whose `code` matches the backend codes (or NETWORK / UNKNOWN),
 * so screens show a translated message instead of raw server text.
 */
const BASE_URL = (import.meta.env?.VITE_API_URL ?? '').replace(/\/+$/, '') + '/api/v1';

export class ApiError extends Error {
  constructor(code, message, status) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

/** A request slower than this shows the "server is waking up" banner. */
export const SLOW_REQUEST_MS = 4000;
/** The free host sleeps when idle: while it wakes, its proxy may answer 502/503/504 or drop the connection. */
const WAKE_STATUSES = new Set([502, 503, 504]);
const RETRY_DELAYS_MS = [3000, 5000, 8000, 12000, 15000];

const slowListeners = new Set();
let slowCount = 0;

/** Subscribes to "a request is unusually slow" changes; returns the unsubscribe function. */
export function onSlowRequests(listener) {
  slowListeners.add(listener);
  return () => slowListeners.delete(listener);
}

function trackSlow(delta) {
  slowCount += delta;
  slowListeners.forEach((listener) => listener(slowCount > 0));
}

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function request(path, options = {}, fetchImpl = globalThis.fetch, retryDelays = RETRY_DELAYS_MS) {
  let slow = false;
  const timer = setTimeout(() => {
    slow = true;
    trackSlow(1);
  }, SLOW_REQUEST_MS);
  try {
    return await requestWithRetry(path, options, fetchImpl, retryDelays);
  } finally {
    clearTimeout(timer);
    if (slow) trackSlow(-1);
  }
}

/** Retries while the backend wakes up; every route is safe to repeat (reads, or stateless computations). */
async function requestWithRetry(path, options, fetchImpl, retryDelays) {
  for (let attempt = 0; ; attempt += 1) {
    try {
      return await requestOnce(path, options, fetchImpl);
    } catch (error) {
      const waking = error.code === 'NETWORK' || (WAKE_STATUSES.has(error.status) && error.code === 'UNKNOWN');
      if (!waking || attempt >= retryDelays.length) throw error;
      await wait(retryDelays[attempt]);
    }
  }
}

async function requestOnce(path, options = {}, fetchImpl = globalThis.fetch) {
  let response;
  try {
    response = await fetchImpl(BASE_URL + path, options);
  } catch (cause) {
    throw new ApiError('NETWORK', cause?.message ?? 'network error', 0);
  }
  if (!response.ok) {
    let body = null;
    try {
      body = await response.json();
    } catch {
      // not JSON: keep the generic error
    }
    throw new ApiError(body?.code ?? 'UNKNOWN', body?.message ?? response.statusText, response.status);
  }
  return response;
}

const json = (path, options, fetchImpl, retryDelays) => request(path, options, fetchImpl, retryDelays).then((r) => r.json());

const post = (body) => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export function createApi(fetchImpl = globalThis.fetch, retryDelays = RETRY_DELAYS_MS) {
  const enc = encodeURIComponent;
  const get = (path) => json(path, undefined, fetchImpl, retryDelays);
  return {
    schools: () => get('/schools'),
    terms: (schoolId) => get(`/schools/${enc(schoolId)}/terms`),
    searchPrograms: (schoolId, q) => get(`/schools/${enc(schoolId)}/programs?q=${enc(q ?? '')}`),
    programCourses: (schoolId, code) => get(`/schools/${enc(schoolId)}/programs/${enc(code)}/courses`),
    sections: (schoolId, termId, code) =>
      get(`/schools/${enc(schoolId)}/terms/${enc(termId)}/courses/${enc(code)}/sections`),
    conflicts: (body) => json('/schedule/conflicts', post(body), fetchImpl, retryDelays),
    rank: (body) => json('/schedule/rank', post(body), fetchImpl, retryDelays),
    /** Returns { blob, filename }. */
    exportIcs: async (body) => {
      const response = await request('/schedule/export', post(body), fetchImpl, retryDelays);
      const disposition = response.headers.get('Content-Disposition') ?? '';
      const match = /filename="?([^";]+)"?/.exec(disposition);
      return { blob: await response.blob(), filename: match ? match[1] : 'horaire.ics' };
    },
  };
}

export const api = createApi((...args) => globalThis.fetch(...args));

/** Wakes the backend as soon as the page opens, so it is ready by the time the user needs it. */
export function warmUp(fetchImpl = globalThis.fetch) {
  return fetchImpl(BASE_URL + '/schools').catch(() => {});
}
