import { listClientEndpoints } from '../api/clientEndpoint.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import SearchField from '../components/SearchField.tsx';
import Spacer from '../components/Spacer.tsx';
import ImportClientEndpointsDialog from '../features/ImportClientEndpointsDialog.tsx';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import {
    Button,
    CircularProgress,
    FormControl,
    Link,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
} from '@mui/material';

import React, { useState } from 'react';
import { useQuery } from 'react-query';
import { Link as RouterLink } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>Client Endpoints</b> page provides overview of all client endpoints registered in Mica.
        Use the <b>import</b> feature to import endpoints using the latest available client data.
    </>
);

const ClientEndpointListPage = () => {
    const [openImport, setOpenImport] = useState(false);

    const [search, setSearch] = useState<string>('');
    const { data, isFetching } = useQuery(
        ['clientEndpoint', 'list', search],
        () => listClientEndpoints(search),
        {
            keepPreviousData: true,
            select: ({ data }) => data,
        },
    );

    return (
        <>
            <PageTitle help={HELP}>Client Endpoints</PageTitle>
            <ImportClientEndpointsDialog
                open={openImport}
                onSuccess={() => setOpenImport(false)}
                onClose={() => setOpenImport(false)}
            />
            <ActionBar>
                <FormControl>
                    <Button
                        startIcon={<CloudUploadIcon />}
                        variant="contained"
                        onClick={() => setOpenImport(true)}>
                        Import
                    </Button>
                </FormControl>
                <Spacer />
                <SearchField onChange={(value) => setSearch(value)} />
            </ActionBar>
            <TableContainer component={Paper}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell width="10%">Status</TableCell>
                            <TableCell width="20%">Client</TableCell>
                            <TableCell>URI</TableCell>
                            <TableCell align="right">
                                {isFetching && (
                                    <CircularProgress size={12} sx={{ marginRight: 1 }} />
                                )}
                            </TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data &&
                            data.length > 0 &&
                            data.map((row) => (
                                <TableRow key={row.id}>
                                    <TableCell>{row.lastKnownStatus}</TableCell>
                                    <TableCell>
                                        <Link
                                            component={RouterLink}
                                            to={`/client/${row.clientName}/details`}>
                                            {row.clientName}
                                        </Link>
                                    </TableCell>
                                    <TableCell>
                                        <Link
                                            component={RouterLink}
                                            to={`/clientEndpoint/${row.id}/details`}>
                                            {row.uri}
                                        </Link>
                                    </TableCell>
                                    <TableCell align="right"></TableCell>
                                </TableRow>
                            ))}
                        {data && data.length < 1 && (
                            <TableRow>
                                <TableCell colSpan={3} align="center">
                                    No data
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </TableContainer>
        </>
    );
};

export default ClientEndpointListPage;
