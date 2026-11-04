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

async function request(path, options = {}, fetchImpl = globalThis.fetch) {
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

const json = (path, options, fetchImpl) => request(path, options, fetchImpl).then((r) => r.json());

const post = (body) => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export function createApi(fetchImpl = globalThis.fetch) {
  const enc = encodeURIComponent;
  return {
    schools: () => json('/schools', undefined, fetchImpl),
    terms: (schoolId) => json(`/schools/${enc(schoolId)}/terms`, undefined, fetchImpl),
    searchPrograms: (schoolId, q) =>
      json(`/schools/${enc(schoolId)}/programs?q=${enc(q ?? '')}`, undefined, fetchImpl),
    programCourses: (schoolId, code) =>
      json(`/schools/${enc(schoolId)}/programs/${enc(code)}/courses`, undefined, fetchImpl),
    sections: (schoolId, termId, code) =>
      json(`/schools/${enc(schoolId)}/terms/${enc(termId)}/courses/${enc(code)}/sections`, undefined, fetchImpl),
    conflicts: (body) => json('/schedule/conflicts', post(body), fetchImpl),
    rank: (body) => json('/schedule/rank', post(body), fetchImpl),
    /** Returns { blob, filename }. */
    exportIcs: async (body) => {
      const response = await request('/schedule/export', post(body), fetchImpl);
      const disposition = response.headers.get('Content-Disposition') ?? '';
      const match = /filename="?([^";]+)"?/.exec(disposition);
      return { blob: await response.blob(), filename: match ? match[1] : 'horaire.ics' };
    },
  };
}

export const api = createApi((...args) => globalThis.fetch(...args));
