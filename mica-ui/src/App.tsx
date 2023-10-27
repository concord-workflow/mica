import { WithUserContext, useCurrentUser } from './UserContext.tsx';
import MainLayout from './layout/MainLayout.tsx';
import { Box, CircularProgress, Typography } from '@mui/material';

import { PropsWithChildren } from 'react';
import { Outlet } from 'react-router-dom';

const WaitForLogin = ({ children }: PropsWithChildren) => {
    const currentUser = useCurrentUser();

    if (!currentUser.username) {
        return (
            <Box
                display="flex"
                width="100vw"
                height="100vh"
                alignItems="center"
                justifyContent="center">
                <Typography variant="h5">Logging in...</Typography>
                <CircularProgress sx={{ marginLeft: 1 }} />
            </Box>
        );
    }

    return <>{children}</>;
};

const App = () => {
    return (
        <WithUserContext>
            <WaitForLogin>
                <MainLayout>
                    <Outlet />
                </MainLayout>
            </WaitForLogin>
        </WithUserContext>
    );
};

export default App;
