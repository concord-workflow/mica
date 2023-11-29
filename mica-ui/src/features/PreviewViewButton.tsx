import { PreviewRequest } from '../api/view.ts';
import PreviewView from './PreviewView.tsx';
import PreviewIcon from '@mui/icons-material/Preview';
import { Button, Dialog, DialogContent, DialogTitle, Tooltip } from '@mui/material';

import React from 'react';

interface Props {
    source: () => PreviewRequest | undefined;
}

const PreviewViewButton = ({ source }: Props) => {
    const [openPreview, setOpenPreview] = React.useState(false);

    return (
        <>
            <Tooltip title="Render the view using a small subset of data.">
                <Button
                    startIcon={<PreviewIcon />}
                    variant="outlined"
                    onClick={() => setOpenPreview(true)}>
                    Preview Data
                </Button>
            </Tooltip>

            <Dialog open={openPreview} onClose={() => setOpenPreview(false)}>
                <DialogTitle>Preview</DialogTitle>
                <DialogContent>
                    <PreviewView source={source} />
                </DialogContent>
            </Dialog>
        </>
    );
};

export default PreviewViewButton;
