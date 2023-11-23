import HelpIcon from '@mui/icons-material/Help';
import { Box, Drawer, IconButton, Typography } from '@mui/material';

import React, { PropsWithChildren, useState } from 'react';

interface Props {
    help?: React.ReactNode;
}

const PageTitle = ({ children, help }: PropsWithChildren<Props>) => {
    const [openDrawer, setOpenDrawer] = useState(false);
    return (
        <>
            <Typography variant="h5" marginBottom={2}>
                {children}
                {help && (
                    <>
                        <IconButton
                            sx={{ alignSelf: 'flex-end' }}
                            onClick={() => setOpenDrawer(true)}>
                            <HelpIcon />
                        </IconButton>
                    </>
                )}
            </Typography>
            <Drawer anchor="right" open={openDrawer} onClose={() => setOpenDrawer(false)}>
                <Box width="100%" maxWidth={500} paddingLeft={2} paddingRight={2} paddingTop={10}>
                    {help}
                </Box>
            </Drawer>
        </>
    );
};

export default PageTitle;
