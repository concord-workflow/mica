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
    schema: string;
}

export interface NewProfile {
    name: string;
    schema: string;
}

export const listClientProfiles = (search?: string): Promise<ProfileList> =>
    doFetch(`/api/mica/v1/profile?search=${search ?? ''}`).then(handleJsonResponse<ProfileList>);

export const getProfile = (name: string): Promise<Profile> =>
    doFetch(`/api/mica/v1/profile/${name}`).then(handleJsonResponse<Profile>);

export const createProfile = (profile: NewProfile): Promise<ProfileEntry> =>
    doFetch(`/api/mica/v1/profile`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(profile),
    }).then(handleJsonResponse<ProfileEntry>);

export const updateProfile = (profile: Profile): Promise<ProfileEntry> =>
    doFetch(`/api/mica/v1/profile`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(profile),
    }).then(handleJsonResponse<ProfileEntry>);
