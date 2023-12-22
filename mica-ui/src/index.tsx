import { createRouter } from './router.tsx';
import WithMicaTheme from './theme.tsx';
import CssBaseline from '@mui/material/CssBaseline';

import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from 'react-query';
import { RouterProvider } from 'react-router-dom';

import '@fontsource/roboto-mono/400.css';
import '@fontsource/roboto/300.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';

const queryClient = new QueryClient();
const router = createRouter();

ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
        <CssBaseline>
            <WithMicaTheme>
                <QueryClientProvider client={queryClient}>
                    <RouterProvider router={router} />
                </QueryClientProvider>
            </WithMicaTheme>
        </CssBaseline>
    </React.StrictMode>,
);
