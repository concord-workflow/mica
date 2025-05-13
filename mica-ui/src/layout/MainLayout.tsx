import Version from '../features/Version.tsx';
import ProfileToolbarButton from '../features/profile/ProfileToolbarButton.tsx';
import ApiIcon from '@mui/icons-material/Api';
import { AppBar, Box, Button, Toolbar, Typography } from '@mui/material';

import { PropsWithChildren } from 'react';
import { Link } from 'react-router-dom';

const MainLayout = ({ children }: PropsWithChildren) => {
    return (
        <Box sx={{ display: 'flex' }}>
            <AppBar position="absolute">
                <Toolbar>
                    <Typography
                        component={Link}
                        to="/"
                        variant="h6"
                        color="inherit"
                        noWrap
                        sx={{ textDecoration: 'none' }}>
                        Mica
                    </Typography>
                    <Box flexGrow={1} />
                    <Button
                        component={Link}
                        to="/api"
                        variant="outlined"
                        color="inherit"
                        startIcon={<ApiIcon />}>
                        API
                    </Button>
                    <Typography variant="caption" sx={{ ml: 2, mr: 2 }}>
                        <Version />
                    </Typography>
                    <ProfileToolbarButton />
                </Toolbar>
            </AppBar>
            <Box
                component="main"
                sx={{
                    backgroundColor: (theme) => theme.palette.grey[100],
                    height: '100vh',
                    overflow: 'auto',
                    display: 'flex',
                    flexGrow: 1,
                    flexDirection: 'column',
                }}>
                <Toolbar />
                {children}
            </Box>
        </Box>
    );
};

export default MainLayout;
