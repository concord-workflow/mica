export const handleJsonResponse = async <T>(resp: Response): Promise<T> => {
    if (!resp.ok) {
        throw new Error(await parseError(resp));
    }
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
