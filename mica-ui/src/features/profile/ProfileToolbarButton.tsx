import { useCurrentUser } from '../../UserContext.tsx';
import { redirectToLogout } from '../../api/common.ts';
import ApiKeyDialog from '../ApiKeyDialog.tsx';
import ProfileDialog from './ProfileDialog.tsx';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import KeyIcon from '@mui/icons-material/Key';
import LogoutIcon from '@mui/icons-material/Logout';
import { Button, ListItemIcon, ListItemText, Menu, MenuItem } from '@mui/material';

import React, { useState } from 'react';

const ProfileToolbarButton = () => {
    const currentUser = useCurrentUser();

    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
    const menuOpen = Boolean(anchorEl);

    const openMenu = (event: React.MouseEvent<HTMLButtonElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const closeMenu = () => {
        setAnchorEl(null);
    };

    const [profileOpen, setProfileOpen] = React.useState<boolean>(false);

    const openProfile = () => {
        setProfileOpen(true);
    };

    const closeProfile = () => {
        setProfileOpen(false);
    };

    const [apiKeysOpen, setApiKeysOpen] = React.useState<boolean>(false);

    const openApiKeys = () => {
        setApiKeysOpen(true);
    };

    const closeApiKeys = () => {
        setApiKeysOpen(false);
    };

    return (
        <>
            <ProfileDialog open={profileOpen} onClose={closeProfile} user={currentUser} />
            <ApiKeyDialog open={apiKeysOpen} onClose={closeApiKeys} user={currentUser} />
            <Button
                color="inherit"
                startIcon={<AccountCircleIcon />}
                variant="text"
                sx={{ textTransform: 'none' }}
                onClick={openMenu}>
                {currentUser.username}
            </Button>
            <Menu anchorEl={anchorEl} open={menuOpen} onClose={closeMenu}>
                <MenuItem onClick={openProfile}>
                    <ListItemIcon>
                        <AccountCircleIcon />
                    </ListItemIcon>
                    <ListItemText>Profile</ListItemText>
                </MenuItem>
                <MenuItem onClick={openApiKeys}>
                    <ListItemIcon>
                        <KeyIcon />
                    </ListItemIcon>
                    <ListItemText>API keys</ListItemText>
                </MenuItem>
                <MenuItem onClick={redirectToLogout}>
                    <ListItemIcon>
                        <LogoutIcon />
                    </ListItemIcon>
                    <ListItemText>Logout</ListItemText>
                </MenuItem>
            </Menu>
        </>
    );
};

export default ProfileToolbarButton;
