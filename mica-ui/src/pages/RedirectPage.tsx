import { listEntities } from '../api/entity.ts';
import { CircularProgress } from '@mui/material';

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
                const result = await listEntities(undefined, entityName);
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

    return <CircularProgress />;
};

export default RedirectPage;
