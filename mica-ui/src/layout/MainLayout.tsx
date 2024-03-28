import Version from '../features/Version.tsx';
import ProfileToolbarButton from './ProfileToolbarButton.tsx';
import { AppBar, Box, Tab, Tabs, Toolbar, Typography, styled } from '@mui/material';

import { PropsWithChildren } from 'react';
import { Link, useLocation } from 'react-router-dom';

const important = <T,>(value: T): T => (value + ' !important') as never;

const StyledTabs = styled(Tabs)(({ theme }) => ({
    '& .MuiTab-root': {
        color: important(theme.palette.getContrastText(theme.palette.primary.main)),
    },
    '& .Mui-selected': {
        color: important(theme.palette.getContrastText(theme.palette.primary.main)),
        backgroundColor: 'rgba(255, 255, 255, 0.2)',
    },
}));

const pathnameToTabIndex = (pathname: string): number => {
    if (pathname.startsWith('/entity')) {
        return 0;
    }
    if (pathname.startsWith('/library')) {
        return 1;
    }
    if (pathname.startsWith('/api')) {
        return 2;
    }
    return 0;
};

const MainLayout = ({ children }: PropsWithChildren) => {
    const location = useLocation();
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
                        sx={{ textDecoration: 'none', pr: 16 }}>
                        Mica
                    </Typography>
                    <StyledTabs
                        TabIndicatorProps={{ sx: { backgroundColor: 'white' } }}
                        value={pathnameToTabIndex(location.pathname)}>
                        <Tab tabIndex={0} label="Entities" component={Link} to={'/entity'} />
                        <Tab tabIndex={1} label="Libraries" component={Link} to={'/library'} />
                        <Tab tabIndex={2} label="API" component={Link} to={'/api'} />
                    </StyledTabs>
                    <Box flexGrow={1} />
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
