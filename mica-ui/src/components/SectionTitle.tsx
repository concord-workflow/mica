import { Box, Typography } from '@mui/material';

import { PropsWithChildren } from 'react';

const SectionTitle = ({ children }: PropsWithChildren) => {
    return (
        <Box sx={{ mt: 2, mb: 2 }}>
            <Typography variant="h6">{children}</Typography>
        </Box>
    );
};

export default SectionTitle;
