import { useCurrentUser } from '../UserContext.tsx';
import { redirectToLogout } from '../api/common.ts';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import { Button, Menu, MenuItem } from '@mui/material';

import React, { useState } from 'react';

const ProfileToolbarButton = () => {
    const currentUser = useCurrentUser();

    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
    const open = Boolean(anchorEl);

    const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
        setAnchorEl(event.currentTarget);
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
                <MenuItem onClick={redirectToLogout}>Logout</MenuItem>
            </Menu>
        </>
    );
};

export default ProfileToolbarButton;
