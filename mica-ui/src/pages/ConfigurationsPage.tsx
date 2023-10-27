import AddIcon from '@mui/icons-material/Add';
import { Box, BoxProps, Button, Modal, Typography, styled } from '@mui/material';

import { useState } from 'react';

const Container = styled(Box)<BoxProps>(({ theme }) => ({
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    width: '90vw',
    height: '90vh',
    backgroundColor: theme.palette.background.paper,
    border: '1px solid #000',
    boxShadow: '24px',
}));

const ConfigurationsPage = () => {
    const [open, setOpen] = useState(false);
    const handleOpen = () => setOpen(true);
    const handleClose = () => setOpen(false);

    return (
        <>
            <Button startIcon={<AddIcon />} variant="contained" onClick={handleOpen}>
                New
            </Button>
            <Modal open={open} onClose={handleClose}>
                <Container>
                    <Typography>Hello!</Typography>
                </Container>
            </Modal>
        </>
    );
};

export default ConfigurationsPage;
