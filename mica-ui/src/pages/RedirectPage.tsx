import { listEntities } from '../api/entity.ts';
import { Box, CircularProgress } from '@mui/material';

import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

const RedirectPage = () => {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    useEffect(() => {
        const type = searchParams.get('type');
        if (type != 'entityByName') {
            // TODO error handling
            return;
        }

        const entityName = searchParams.get('entityName');
        if (!entityName) {
            // TODO error handling
            return;
        }

        const doIt = async () => {
            try {
                const result = await listEntities({ entityName });
                if (result.data.length != 1) {
                    // TODO error handling
                    return;
                }

                navigate(`/entity/${result.data[0].id}/details`, { replace: true });
            } catch (e) {
                console.error('Error while redirecting to the entity page', e);
                // TODO show error
            }
        };
        doIt();
    }, [navigate, searchParams]);

    return (
        <Box width="100%" height="100%" display="flex" alignItems="center" justifyContent="center">
            Redirecting...
            <CircularProgress sx={{ ml: 2 }} size="24px" />
        </Box>
    );
};

export default RedirectPage;
