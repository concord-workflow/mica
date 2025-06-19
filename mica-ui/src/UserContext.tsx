/* eslint-disable react-refresh/only-export-components */
import { redirectToLogin } from './api/common.ts';

import { useQuery } from '@tanstack/react-query';
import React, { PropsWithChildren, useContext } from 'react';

export interface CurrentUser {
    userId?: string;
    username?: string;
    oidcGroups?: Array<string>;
    roles?: Array<string>;
    teams?: Array<{
        orgName: string;
        teamName: string;
        teamRole: string;
    }>;
}

const UserContext = React.createContext<CurrentUser>({});

async function whoami(): Promise<CurrentUser> {
    const response = await fetch(`/api/mica/ui/whoami`);
    if (response.status == 401) {
        redirectToLogin();
    }
    return (await response.json()) as CurrentUser;
}

export const WithUserContext = ({ children }: PropsWithChildren) => {
    const { data } = useQuery({
        queryKey: ['whoami'],
        queryFn: whoami,
        initialData: {},
        refetchOnReconnect: true,
        refetchOnWindowFocus: true,
    });

    return <UserContext.Provider value={data}>{children}</UserContext.Provider>;
};

export const useCurrentUser = () => {
    return useContext(UserContext);
};
