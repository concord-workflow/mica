import { createRouter } from './router.tsx';
import WithMicaTheme from './theme.tsx';
import CssBaseline from '@mui/material/CssBaseline';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';

import '@fontsource/fira-mono/400.css';
import '@fontsource/fira-mono/500.css';
import '@fontsource/fira-mono/700.css';
import '@fontsource/fira-sans/300.css';
import '@fontsource/fira-sans/400.css';
import '@fontsource/fira-sans/500.css';
import '@fontsource/fira-sans/700.css';

const queryClient = new QueryClient();
const router = createRouter();

ReactDOM.createRoot(document.getElementById('root')!).render(
    <WithMicaTheme>
        <CssBaseline>
            <QueryClientProvider client={queryClient}>
                <RouterProvider router={router} />
            </QueryClientProvider>
        </CssBaseline>
    </WithMicaTheme>,
);
