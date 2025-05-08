/* eslint-disable react-refresh/only-export-components */
import { redirectToLogin } from './api/common.ts';

import React, { PropsWithChildren, useContext, useEffect, useState } from 'react';

interface CurrentUser {
    username?: string;
}

const UserContext = React.createContext<CurrentUser>({});

export const WithUserContext = ({ children }: PropsWithChildren) => {
    const [currentUser, setCurrentUser] = useState<CurrentUser>({});

    useEffect(() => {
        if (currentUser.username) {
            return;
        }

        const doIt = async () => {
            try {
                const response = await fetch(`/api/mica/ui/whoami`);
                const json = await response.json();
                setCurrentUser({ username: json.username });
            } catch (_e) {
                redirectToLogin();
            }
        };

        doIt();
    }, [currentUser.username]);

    return <UserContext.Provider value={currentUser}>{children}</UserContext.Provider>;
};

export const useCurrentUser = () => {
    return useContext(UserContext);
};
