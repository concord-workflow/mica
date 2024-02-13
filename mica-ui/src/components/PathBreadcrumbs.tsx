import HomeIcon from '@mui/icons-material/Home';
import { Link, Tooltip, styled } from '@mui/material';

import React, { PropsWithChildren } from 'react';
import { Link as RouterLink } from 'react-router-dom';

const InlineHomeIcon = styled(HomeIcon)(() => ({
    position: 'relative',
    top: '3px',
}));

const PathBreadcrumbs = ({ path, children }: PropsWithChildren<{ path: string }>) => {
    const parts = React.useMemo(() => {
        const items = path.split('/').filter((part) => part.length > 0);
        return items.map((part, index) => {
            if (index == items.length - 1) {
                if (children) {
                    return children;
                }
                return <React.Fragment key={index}>&nbsp;/{part}</React.Fragment>;
            }
            return (
                <React.Fragment key={index}>
                    &nbsp;
                    <Link
                        component={RouterLink}
                        to={`/entity?path=/${items.slice(0, index + 1).join('/')}`}>
                        {'/'}
                        {part}
                    </Link>
                </React.Fragment>
            );
        });
    }, [path, children]);

    return (
        <>
            <Tooltip title="Back to /">
                <Link component={RouterLink} to={`/entity?path=/`}>
                    <InlineHomeIcon fontSize="small" />
                </Link>
            </Tooltip>
            {parts}
        </>
    );
};

export default PathBreadcrumbs;
