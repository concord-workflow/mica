import { Stack } from '@mui/material';

import { PropsWithChildren } from 'react';

const ActionBar = ({ children }: PropsWithChildren) => {
    return (
        <Stack direction="row" spacing={2} marginBottom={2}>
            {children}
        </Stack>
    );
};

export default ActionBar;
