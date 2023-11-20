import App from './App.tsx';
import ApiPage from './pages/ApiPage.tsx';
import ClientDetailsPage from './pages/ClientDetailsPage.tsx';
import ClientEndpointListPage from './pages/ClientEndpointListPage.tsx';
import ClientListPage from './pages/ClientListPage.tsx';
import ProfileDetailsPage from './pages/ProfileDetailsPage.tsx';
import ProfileListPage from './pages/ProfileListPage.tsx';

import { createBrowserRouter } from 'react-router-dom';

export const createRouter = () =>
    createBrowserRouter(
        [
            {
                path: '/',
                element: <App />,
                children: [
                    {
                        path: 'api',
                        element: <ApiPage />,
                    },
                    {
                        path: 'client',
                        element: <ClientListPage />,
                    },
                    {
                        path: 'client/:clientName/details',
                        element: <ClientDetailsPage />,
                    },
                    {
                        path: 'clientEndpoint',
                        element: <ClientEndpointListPage />,
                    },
                    {
                        path: 'profile',
                        element: <ProfileListPage />,
                    },
                    {
                        path: 'profile/:profileName/details',
                        element: <ProfileDetailsPage />,
                    },
                ],
            },
        ],
        {
            basename: '/mica',
        },
    );
