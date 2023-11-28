/* eslint-disable react-refresh/only-export-components */
import { styled } from '@mui/material';

import React from 'react';

const HighlightSpan = styled('span')(({ theme }) => ({
    backgroundColor: theme.palette.info.main,
    color: theme.palette.primary.contrastText,
}));

const highlightSubstring = (s: string, search: string): React.ReactNode => {
    if (search == undefined || search == '') {
        return <>{s}</>;
    }
    if (s == undefined) {
        return null;
    }
    const searchLower = search.toLowerCase();
    const index = s.toLowerCase().indexOf(searchLower);
    if (index < 0) {
        return s;
    }
    return (
        <>
            {s.substring(0, index)}
            <HighlightSpan>{s.substring(index, index + search.length)}</HighlightSpan>
            {s.substring(index + search.length)}
        </>
    );
};

export default highlightSubstring;
