import RestoreIcon from '@mui/icons-material/Restore';
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
} from '@mui/material';

import React from 'react';

interface Props {
    disabled: boolean;
    onConfirm: () => void;
}

const ResetButton = ({ disabled, onConfirm }: Props) => {
    const [open, setOpen] = React.useState(false);

    const handleOpen = React.useCallback(() => setOpen(true), []);
    const handleConfirm = React.useCallback(() => {
        setOpen(false);
        onConfirm();
    }, [onConfirm]);

    const handleClose = React.useCallback(() => setOpen(false), []);

    return (
        <>
            <Dialog open={open} onClose={handleClose}>
                <DialogTitle>Revert changes?</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Are you sure you want to revert any changes?
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button variant="contained" onClick={handleClose} autoFocus={true}>
                        Cancel
                    </Button>
                    <Button onClick={handleConfirm}>Reset</Button>
                </DialogActions>
            </Dialog>
            <Button
                disabled={disabled}
                startIcon={<RestoreIcon />}
                variant="contained"
                onClick={handleOpen}>
                Reset
            </Button>
        </>
    );
};

export default ResetButton;
