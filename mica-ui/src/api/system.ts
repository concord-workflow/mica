import { doFetch, handleJsonResponse } from './common.ts';

export interface SystemInfo {
    version: string;
}

export const getSystemInfo = (): Promise<SystemInfo> =>
    doFetch('/api/mica/v1/system').then(handleJsonResponse<SystemInfo>);
