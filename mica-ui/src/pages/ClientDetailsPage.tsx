import { getLatestData } from '../api/clientData.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import SearchField from '../components/SearchField.tsx';
import Spacer from '../components/Spacer.tsx';
import highlightSubstring from '../components/highlight.tsx';
import {
    CircularProgress,
    Container,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';

import React from 'react';
import { useQuery } from 'react-query';
import { useParams } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>Client Details</b> page provides overview of all client-related data in Mica.
    </>
);

type RouteParams = {
    clientName: string;
};

const renderPropertyValue = (o: object, key: string): string =>
    JSON.stringify((o as Record<string, object>)[key], null, 2);

const searchProperties = (o: object, search: string): Array<string> => {
    const searchLower = search.toLowerCase();
    return Object.keys(o).filter((key) => key.toLowerCase().includes(searchLower));
};

const ClientDetailsPage = () => {
    const { clientName } = useParams<RouteParams>();

    const { data, isFetching } = useQuery(
        ['client', 'data', clientName],
        () => getLatestData(clientName!),
        {
            enabled: clientName !== undefined,
        },
    );

    const [search, setSearch] = React.useState<string>('');

    return (
        <>
            <PageTitle help={HELP}>Client Details</PageTitle>
            <Container maxWidth="lg">
                <Typography variant="h5" sx={{ marginBottom: 1 }}>
                    {clientName} {isFetching && <CircularProgress size={16} />}
                </Typography>
                {data && (
                    <>
                        <Typography variant="h6">Properties</Typography>
                        <ActionBar>
                            <Spacer />
                            <SearchField onChange={(search) => setSearch(search)} />
                        </ActionBar>
                        <TableContainer component={Paper}>
                            <Table size="small">
                                <TableHead>
                                    <TableRow>
                                        <TableCell width="30%">Property</TableCell>
                                        <TableCell width="60%">Value</TableCell>
                                        <TableCell width="10%" align="right">
                                            {isFetching && (
                                                <CircularProgress
                                                    size={24}
                                                    sx={{ marginRight: 1 }}
                                                />
                                            )}
                                        </TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {searchProperties(data.properties, search)
                                        .sort()
                                        .map((key) => (
                                            <TableRow key={key}>
                                                <TableCell sx={{ verticalAlign: 'top' }}>
                                                    {highlightSubstring(key, search)}
                                                </TableCell>
                                                <TableCell>
                                                    <pre>
                                                        {renderPropertyValue(data.properties, key)}
                                                    </pre>
                                                </TableCell>
                                                <TableCell></TableCell>
                                            </TableRow>
                                        ))}
                                </TableBody>
                            </Table>
                        </TableContainer>
                    </>
                )}
            </Container>
        </>
    );
};

export default ClientDetailsPage;
