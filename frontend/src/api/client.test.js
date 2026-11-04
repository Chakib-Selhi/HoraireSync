import { describe, expect, it, vi } from 'vitest';
import { ApiError, createApi } from './client.js';

const jsonResponse = (status, body, headers = {}) => ({
  ok: status >= 200 && status < 300,
  status,
  statusText: 'status',
  headers: { get: (name) => headers[name] ?? null },
  json: () => Promise.resolve(body),
  blob: () => Promise.resolve(new Blob(['BEGIN:VCALENDAR'])),
});

describe('api client', () => {
  it('calls the versioned routes and encodes parameters', async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse(200, []));
    const api = createApi(fetch);
    await api.searchPrograms('uqam', 'génie logiciel');
    await api.sections('uqam', '20271', 'INF1120');
    expect(fetch.mock.calls[0][0]).toBe('/api/v1/schools/uqam/programs?q=g%C3%A9nie%20logiciel');
    expect(fetch.mock.calls[1][0]).toBe('/api/v1/schools/uqam/terms/20271/courses/INF1120/sections');
  });

  it('posts JSON bodies', async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse(200, []));
    await createApi(fetch).conflicts({ selections: [] });
    const [, options] = fetch.mock.calls[0];
    expect(options.method).toBe('POST');
    expect(options.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(options.body)).toEqual({ selections: [] });
  });

  it('turns the error format into an ApiError with its code', async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse(404, { code: 'COURSE_NOT_FOUND', message: 'introuvable' }));
    await expect(createApi(fetch).sections('uqam', '20271', 'ZZZ9999')).rejects.toMatchObject({
      name: 'ApiError', code: 'COURSE_NOT_FOUND', status: 404, message: 'introuvable',
    });
  });

  it('reports network failures and non JSON errors', async () => {
    await expect(createApi(vi.fn().mockRejectedValue(new TypeError('offline'))).schools())
      .rejects.toMatchObject({ code: 'NETWORK' });
    const notJson = { ...jsonResponse(502, null), json: () => Promise.reject(new Error('html')) };
    await expect(createApi(vi.fn().mockResolvedValue(notJson)).schools()).rejects.toBeInstanceOf(ApiError);
    await expect(createApi(vi.fn().mockResolvedValue(notJson)).schools()).rejects.toMatchObject({ code: 'UNKNOWN' });
  });

  it('returns the calendar file and its name', async () => {
    const fetch = vi.fn().mockResolvedValue(
      jsonResponse(200, null, { 'Content-Disposition': 'attachment; filename="horaire-uqam-20271.ics"' }),
    );
    const { blob, filename } = await createApi(fetch).exportIcs({});
    expect(filename).toBe('horaire-uqam-20271.ics');
    expect(blob).toBeInstanceOf(Blob);
  });
});
