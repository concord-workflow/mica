import App from './App.tsx';
import ClientDetailsPage from './pages/ClientDetailsPage.tsx';
import ClientListPage from './pages/ClientListPage.tsx';
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
                        path: 'client',
                        element: <ClientListPage />,
                    },
                    {
                        path: 'client/:clientId/details',
                        element: <ClientDetailsPage />,
                    },
                    {
                        path: 'profile',
                        element: <ProfileListPage />,
                    },
                ],
            },
        ],
        {
            basename: '/mica',
        },
    );
