import { parseError } from './error.ts';

export const doFetch = async (input: RequestInfo | URL, init?: RequestInit) =>
    fetch(input, addUiHeader(init));

const addUiHeader = (init?: RequestInit): RequestInit => {
    // add X-Concord-UI-Request header
    // that's how concord-server knows not to respond with 'WWW-Authenticate: Basic'
    // which triggers the browser's basic auth popup, and we don't want that
    const headers = new Headers(init?.headers);
    headers.append('X-Concord-UI-Request', 'true');
    return { ...init, headers };
};

export const redirectToLogin = () => {
    const url = new URL(window.location.href);
    url.pathname = `/api/service/oidc/auth`; //
    url.search = `?from=${encodeURIComponent(window.location.href)}`;
    console.log('Redirecting to login: ' + url.toString());
    window.location.assign(url);
};

export const redirectToLogout = () => {
    const from = new URL(window.location.href);
    from.pathname = '/mica/';
    from.search = '';
    const url = new URL(window.location.href);
    url.pathname = `/api/service/oidc/logout`;
    url.search = `?from=${encodeURIComponent(from.toString())}`;
    console.log('Redirecting to logout: ' + url.toString());
    window.location.assign(url);
};

export const handleErrors = async (resp: Response) => {
    if (!resp.ok) {
        throw await parseError(resp);
    }
};

export const handleJsonResponse = async <T>(resp: Response): Promise<T> => {
    await handleErrors(resp);
    const data = await resp.json();
    return data as T;
};

export const handleTextResponse = async (resp: Response): Promise<string> => {
    await handleErrors(resp);
    return await resp.text();
};
