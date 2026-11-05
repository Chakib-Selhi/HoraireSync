import { describe, expect, it, vi } from 'vitest';
import { ApiError, createApi, onSlowRequests, SLOW_REQUEST_MS } from './client.js';

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
    await expect(createApi(vi.fn().mockRejectedValue(new TypeError('offline')), []).schools())
      .rejects.toMatchObject({ code: 'NETWORK' });
    const notJson = { ...jsonResponse(502, null), json: () => Promise.reject(new Error('html')) };
    await expect(createApi(vi.fn().mockResolvedValue(notJson), []).schools()).rejects.toBeInstanceOf(ApiError);
    await expect(createApi(vi.fn().mockResolvedValue(notJson), []).schools()).rejects.toMatchObject({ code: 'UNKNOWN' });
  });

  it('returns the calendar file and its name', async () => {
    const fetch = vi.fn().mockResolvedValue(
      jsonResponse(200, null, { 'Content-Disposition': 'attachment; filename="horaire-uqam-20271.ics"' }),
    );
    const { blob, filename } = await createApi(fetch).exportIcs({});
    expect(filename).toBe('horaire-uqam-20271.ics');
    expect(blob).toBeInstanceOf(Blob);
  });

  it('retries while the server wakes up, but not on business errors', async () => {
    const asleep = { ...jsonResponse(503, null), json: () => Promise.reject(new Error('html')) };
    const fetch = vi.fn()
      .mockRejectedValueOnce(new TypeError('connection reset'))
      .mockResolvedValueOnce(asleep)
      .mockResolvedValueOnce(jsonResponse(200, [{ id: 'uqam' }]));
    await expect(createApi(fetch, [0, 0, 0]).schools()).resolves.toEqual([{ id: 'uqam' }]);
    expect(fetch).toHaveBeenCalledTimes(3);

    const business = vi.fn().mockResolvedValue(jsonResponse(503, { code: 'SOURCE_UNAVAILABLE', message: 'x' }));
    await expect(createApi(business, [0, 0]).schools()).rejects.toMatchObject({ code: 'SOURCE_UNAVAILABLE' });
    expect(business).toHaveBeenCalledTimes(1);
  });

  it('signals slow requests so the page can say the server is starting', async () => {
    vi.useFakeTimers();
    const states = [];
    const unsubscribe = onSlowRequests((slow) => states.push(slow));
    let respond;
    const fetch = vi.fn(() => new Promise((resolve) => { respond = resolve; }));
    const pending = createApi(fetch, []).schools();

    await vi.advanceTimersByTimeAsync(SLOW_REQUEST_MS + 10);
    expect(states).toEqual([true]);
    respond(jsonResponse(200, []));
    await pending;
    expect(states).toEqual([true, false]);
    unsubscribe();
    vi.useRealTimers();
  });
});
