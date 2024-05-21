import { createRouter } from './router.tsx';
import WithMicaTheme from './theme.tsx';
import CssBaseline from '@mui/material/CssBaseline';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';

import '@fontsource/roboto-mono/400.css';
import '@fontsource/roboto/300.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';

const queryClient = new QueryClient();
const router = createRouter();

ReactDOM.createRoot(document.getElementById('root')!).render(
    <CssBaseline>
        <WithMicaTheme>
            <QueryClientProvider client={queryClient}>
                <RouterProvider router={router} />
            </QueryClientProvider>
        </WithMicaTheme>
    </CssBaseline>,
);
