import { Box, CircularProgress, useColorScheme } from '@mui/material';

import { Suspense, lazy } from 'react';
import 'swagger-ui-react/swagger-ui.css';

import './ApiPage.css';

const SwaggerUI = lazy(() => import('swagger-ui-react'));

const useClassName = (): string | undefined => {
    const { mode, systemMode } = useColorScheme();
    if (mode === 'dark' || (mode === 'system' && systemMode === 'dark')) {
        return 'dark';
    }
};

const ApiPage = () => {
    const className = useClassName();

    return (
        <Suspense
            fallback={
                <Box
                    width="100%"
                    height="100%"
                    display="flex"
                    justifyContent="center"
                    alignItems="center">
                    <CircularProgress />
                </Box>
            }>
            <span className={className}>
                <SwaggerUI url="/api/mica/swagger.json" />
            </span>
        </Suspense>
    );
};

export default ApiPage;
