import { getLatestData } from '../api/clientData.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import SearchField from '../components/SearchField.tsx';
import Spacer from '../components/Spacer.tsx';
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
    clientId: string;
};

const ClientDetailsPage = () => {
    const { clientId } = useParams<RouteParams>();
    const { data, isFetching } = useQuery(
        ['client', 'data', clientId],
        () => getLatestData(clientId!),
        {
            enabled: clientId !== undefined,
        },
    );

    return (
        <>
            <PageTitle help={HELP}>Client Details</PageTitle>
            <Container maxWidth="lg">
                <Typography variant="h5" sx={{ marginBottom: 1 }}>
                    Metadata
                </Typography>
                <Paper sx={{ padding: 3, marginBottom: 5 }}>TODO</Paper>

                {data && (
                    <>
                        <Typography variant="h5">Properties</Typography>
                        <ActionBar>
                            <Spacer />
                            <SearchField onChange={(search) => console.log('!TODO', { search })} />
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
                                    {Object.keys(data.properties)
                                        .sort()
                                        .map((key) => (
                                            <TableRow key={key}>
                                                <TableCell sx={{ verticalAlign: 'top' }}>
                                                    {key}
                                                </TableCell>
                                                <TableCell>
                                                    <pre>
                                                        {JSON.stringify(
                                                            (
                                                                data.properties as Record<
                                                                    string,
                                                                    object
                                                                >
                                                            )[key],
                                                            null,
                                                            2,
                                                        )}
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
