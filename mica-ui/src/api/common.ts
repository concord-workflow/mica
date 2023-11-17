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

export const handleErrors = async (resp: Response) => {
    if (!resp.ok) {
        if (resp.status == 401) {
            window.location.pathname = '/api/mica/oidc/login';
        }

        throw new Error(await parseError(resp));
    }
};

export const handleJsonResponse = async <T>(resp: Response): Promise<T> => {
    await handleErrors(resp);
    const data = await resp.json();
    return data as T;
};

const parseError = async (resp: Response): Promise<string> => {
    if (!resp.headers.get('content-type')?.includes('application/json')) {
        return `${resp.status} ${resp.statusText}`;
    }

    const error = await resp.json();
    const message = error.message;
    if (!message) {
        return JSON.stringify(error);
    }
    return message;
};
