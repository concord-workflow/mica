import App from './App.tsx';
import ApiPage from './pages/ApiPage.tsx';
import DashboardPage from './pages/DashboardPage.tsx';
import DeletedEntityListPage from './pages/DeletedEntityListPage.tsx';
import DocumentationPage from './pages/DocumentationPage.tsx';
import EditEntityPage from './pages/EditEntityPage.tsx';
import EntityDetailsPage from './pages/EntityDetailsPage.tsx';
import EntityListPage from './pages/EntityListPage.tsx';
import RedirectPage from './pages/RedirectPage.tsx';

import { Navigate, createBrowserRouter } from 'react-router-dom';

export const createRouter = () =>
    createBrowserRouter(
        [
            {
                path: '/',
                element: <App />,
                children: [
                    {
                        path: '/',
                        element: <Navigate to="/entity" replace={true} />,
                    },
                    {
                        path: 'api',
                        element: <ApiPage />,
                    },
                    {
                        path: 'redirect',
                        element: <RedirectPage />,
                    },
                    {
                        path: 'entity',
                        element: <EntityListPage />,
                    },
                    {
                        path: 'trash',
                        element: <DeletedEntityListPage />,
                    },
                    {
                        path: 'entity/:entityId/details',
                        element: <EntityDetailsPage />,
                    },
                    {
                        path: 'entity/:entityId/edit',
                        element: <EditEntityPage />,
                    },
                    {
                        path: 'dashboard/:entityId',
                        element: <DashboardPage />,
                    },
                    {
                        path: 'documentation',
                        element: <DocumentationPage />,
                    },
                ],
            },
        ],
        {
            basename: '/mica',
        },
    );
