import { Stack, Theme } from '@mui/material';
import { SxProps } from '@mui/system';

import React from 'react';

interface Props {
    sx?: SxProps<Theme>;
    children: React.ReactNode;
}

const ActionBar = ({ sx, children }: Props) => {
    return (
        <Stack direction="row" spacing={2} sx={sx} alignItems="center">
            {children}
        </Stack>
    );
};

export default ActionBar;
