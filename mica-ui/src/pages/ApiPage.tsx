import { CircularProgress } from '@mui/material';

import { Suspense, lazy } from 'react';
import 'swagger-ui-react/swagger-ui.css';

import './ApiPage.css';

const SwaggerUI = lazy(() => import('swagger-ui-react'));

const ApiPage = () => {
    return (
        <Suspense fallback={<CircularProgress />}>
            <SwaggerUI url="/api/mica/swagger.json" />
        </Suspense>
    );
};

export default ApiPage;
