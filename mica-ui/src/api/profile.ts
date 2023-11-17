import { doFetch, handleJsonResponse } from './common.ts';

export interface ProfileEntry {
    id: string;
    name: string;
}

export interface ProfileList {
    data: Array<ProfileEntry>;
}

export interface Profile {
    id: string;
    name: string;
    schema: object;
}

export const listClientProfiles = (search?: string): Promise<ProfileList> =>
    doFetch(`/api/mica/v1/profile?search=${search ?? ''}`).then(handleJsonResponse<ProfileList>);

export const getProfile = (name: string): Promise<Profile> =>
    doFetch(`/api/mica/v1/profile/${name}`).then(handleJsonResponse<Profile>);
