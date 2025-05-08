import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { IconButton, SvgIcon, Tooltip, styled } from '@mui/material';

import React from 'react';

interface Props {
    text: string;
    tooltipText?: string;
    Icon?: typeof SvgIcon;
}

const InlineIconButton = styled(IconButton)(() => ({
    fontSize: 'inherit',
}));

const CopyToClipboardButton = ({
    text,
    tooltipText = 'Copy to clipboard',
    Icon = ContentCopyIcon,
}: Props) => {
    const [tooltip, setTooltip] = React.useState<string>(tooltipText);
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
                setTooltip(tooltipText);
            }, 1000);
        });
    }, [text, tooltipText]);
    return (
        <Tooltip title={tooltip} open={open} onOpen={handleOpen} onClose={handleClose}>
            <InlineIconButton onClick={handleClick}>
                <Icon fontSize="inherit" />
            </InlineIconButton>
        </Tooltip>
    );
};

export default CopyToClipboardButton;
