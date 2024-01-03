import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { IconButton, Tooltip, styled } from '@mui/material';

import React from 'react';

interface Props {
    text: string;
}

const InlineIconButton = styled(IconButton)(() => ({
    fontSize: 'inherit',
}));

const CopyToClipboardButton = ({ text }: Props) => {
    const [tooltip, setTooltip] = React.useState<string>('Copy to clipboard');
    const [open, setOpen] = React.useState<boolean>(false);
    const handleOpen = React.useCallback(() => {
        setOpen(true);
    }, []);
    const handleClose = React.useCallback(() => {
        setOpen(false);
    }, []);
    const handleClick = React.useCallback(() => {
        navigator.clipboard.writeText(text).then(() => {
            setTooltip('Copied!');
            setOpen(true);
            setTimeout(() => {
                setOpen(false);
                setTooltip('Copy to clipboard');
            }, 1000);
        });
    }, [text]);
    return (
        <Tooltip title={tooltip} open={open} onOpen={handleOpen} onClose={handleClose}>
            <InlineIconButton onClick={handleClick}>
                <ContentCopyIcon fontSize="inherit" />
            </InlineIconButton>
        </Tooltip>
    );
};

export default CopyToClipboardButton;
