import { Typography } from '@mui/material';

import { PropsWithChildren } from 'react';

const SectionTitle = ({ children }: PropsWithChildren) => {
    return (
        <>
            <Typography variant="h6">{children}</Typography>
        </>
    );
};

export default SectionTitle;
