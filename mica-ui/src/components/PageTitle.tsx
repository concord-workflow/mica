import HelpIcon from '@mui/icons-material/Help';
import { Box, Drawer, IconButton, Typography } from '@mui/material';

import React, { PropsWithChildren, useState } from 'react';

interface Props {
    help?: React.ReactNode;
}

const PageTitle = ({ children, help }: PropsWithChildren<Props>) => {
    const [openHelp, setOpenHelp] = useState(false);

    const handleHelpIconClick = React.useCallback(() => {
        setOpenHelp(true);
    }, []);

    const handleCloseHelp = React.useCallback(() => {
        setOpenHelp(false);
    }, []);

    return (
        <>
            <Typography variant="h5" marginBottom={2}>
                {children}
                {help && (
                    <>
                        <IconButton onClick={handleHelpIconClick}>
                            <HelpIcon />
                        </IconButton>
                    </>
                )}
            </Typography>
            <Drawer anchor="right" open={openHelp} onClose={handleCloseHelp}>
                <Box width="100%" maxWidth={500} paddingLeft={2} paddingRight={2} paddingTop={10}>
                    {help}
                </Box>
            </Drawer>
        </>
    );
};

export default PageTitle;
