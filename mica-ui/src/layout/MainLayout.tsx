import Version from '../features/Version.tsx';
import ProfileToolbarButton from './ProfileToolbarButton.tsx';
import ApiIcon from '@mui/icons-material/Api';
import { AppBar, Box, Button, Tab, Tabs, Toolbar, Typography, styled } from '@mui/material';

import { PropsWithChildren } from 'react';
import { Link, useLocation } from 'react-router-dom';

type TabKey = 'entity' | 'library';

const StyledTabs = styled(Tabs)(({ theme }) => ({
    '& .MuiTab-root': { color: 'inherit' },
    '& .Mui-selected': {
        color: theme.palette.getContrastText(theme.palette.primary.main),
        backgroundColor: 'rgba(255, 255, 255, 0.2)',
    },
}));

const pathnameToTab = (pathname: string): TabKey => {
    if (pathname.startsWith('/entity')) {
        return 'entity';
    }
    if (pathname.startsWith('/library')) {
        return 'library';
    }
    return 'entity';
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
                        sx={{ textDecoration: 'none', pr: 6 }}>
                        Mica
                    </Typography>
                    <StyledTabs
                        TabIndicatorProps={{ sx: { backgroundColor: 'white' } }}
                        value={pathnameToTab(location.pathname)}>
                        <Tab
                            tabIndex={0}
                            value="entity"
                            label="Entities"
                            component={Link}
                            to={'/entity'}
                        />
                        <Tab
                            tabIndex={1}
                            value="library"
                            label="Libraries"
                            component={Link}
                            to={'/library'}
                        />
                    </StyledTabs>
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
