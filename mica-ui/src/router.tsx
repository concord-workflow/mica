import App from './App.tsx';
import ApiPage from './pages/ApiPage.tsx';
import EditEntityPage from './pages/EditEntityPage.tsx';
import EntityDetailsPage from './pages/EntityDetailsPage.tsx';
import EntityListPage from './pages/EntityListPage.tsx';

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
                        path: 'entity',
                        element: <EntityListPage />,
                    },
                    {
                        path: 'entity/:entityId/details',
                        element: <EntityDetailsPage />,
                    },
                    {
                        path: 'entity/:entityId/edit',
                        element: <EditEntityPage />,
                    },
                ],
            },
        ],
        {
            basename: '/mica',
        },
    );
