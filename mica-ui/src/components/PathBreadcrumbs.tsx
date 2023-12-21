import HomeIcon from '@mui/icons-material/Home';
import { Box, Link, Tooltip } from '@mui/material';

import React from 'react';
import { Link as RouterLink } from 'react-router-dom';

const PathBreadcrumbs = ({ path }: { path: string }) => {
    const parts = path.split('/').filter((part) => part.length > 0);
    return (
        <Box marginBottom={2}>
            <Tooltip title="Back to /">
                <Link component={RouterLink} to={`/entity?path=/`}>
                    <HomeIcon fontSize="small" sx={{ position: 'relative', top: '3px' }} />
                </Link>
            </Tooltip>
            {parts.map((part, index) => {
                if (index == parts.length - 1) {
                    return <React.Fragment key={index}>&nbsp;/{part}</React.Fragment>;
                }
                return (
                    <React.Fragment key={index}>
                        &nbsp;
                        <Link
                            component={RouterLink}
                            to={`/entity?path=/${parts.slice(0, index + 1).join('/')}`}>
                            {'/'}
                            {part}
                        </Link>
                    </React.Fragment>
                );
            })}
        </Box>
    );
};

export default PathBreadcrumbs;
