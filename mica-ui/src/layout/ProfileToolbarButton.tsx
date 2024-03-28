import { useCurrentUser } from '../UserContext.tsx';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import LogoutIcon from '@mui/icons-material/Logout';
import { Button, Menu, MenuItem } from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';

import React, { useState } from 'react';

const ProfileToolbarButton = () => {
    const currentUser = useCurrentUser();

    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
    const open = Boolean(anchorEl);

    const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const handleLogout = () => {
        window.location.pathname = '/api/mica/oidc/logout';
    };

    const handleClose = () => {
        setAnchorEl(null);
    };

    return (
        <>
            <Button
                color="inherit"
                startIcon={<AccountCircleIcon />}
                variant="text"
                sx={{ textTransform: 'none' }}
                onClick={handleClick}>
                {currentUser.username}
            </Button>
            <Menu anchorEl={anchorEl} open={open} onClose={handleClose}>
                <MenuItem onClick={handleLogout}>
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
