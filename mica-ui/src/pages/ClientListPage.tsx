import { listClients } from '../api/client.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import RowMenu from '../components/RowMenu.tsx';
import SearchField from '../components/SearchField.tsx';
import Spacer from '../components/Spacer.tsx';
import ImportDocumentDialog from '../features/ImportDocumentDialog.tsx';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DeleteIcon from '@mui/icons-material/Delete';
import {
    Button,
    CircularProgress,
    FormControl,
    Link,
    MenuItem,
    Paper,
    Snackbar,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
} from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';

import React, { useState } from 'react';
import { useQuery } from 'react-query';
import { Link as RouterLink } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>Clients</b> page provides overview of all clients registered in Mica. Use the{' '}
        <b>upload</b> feature to import client lists in YAML format.
    </>
);

const ClientListPage = () => {
    const [openUpload, setOpenUpload] = useState(false);

    const [search, setSearch] = useState<string>('');
    const { data, isFetching } = useQuery(
        ['client', 'list', search],
        () => listClients(search, ['status']),
        {
            keepPreviousData: true,
            select: ({ data }) => data,
        },
    );

    const [openSuccessNotification, setOpenSuccessNotification] = useState(false);
    const handleSuccessfulUpload = () => {
        setOpenSuccessNotification(true);
        setOpenUpload(false);
    };

    return (
        <>
            <PageTitle help={HELP}>Clients</PageTitle>
            <ImportDocumentDialog
                open={openUpload}
                onSuccess={handleSuccessfulUpload}
                onClose={() => setOpenUpload(false)}
            />
            <Snackbar
                open={openSuccessNotification}
                autoHideDuration={5000}
                onClose={() => setOpenSuccessNotification(false)}
                message="Client data uploaded successfully"
            />
            <ActionBar>
                <FormControl>
                    <Button
                        startIcon={<CloudUploadIcon />}
                        variant="contained"
                        onClick={() => setOpenUpload(true)}>
                        Upload
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
                            <TableCell>Name</TableCell>
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
                                    <TableCell>{row.properties.status}</TableCell>
                                    <TableCell>
                                        <Link
                                            component={RouterLink}
                                            to={`/client/${row.name}/details`}>
                                            {row.name}
                                        </Link>
                                    </TableCell>
                                    <TableCell align="right">
                                        <RowMenu>
                                            <MenuItem disabled={true}>
                                                <ListItemIcon>
                                                    <DeleteIcon fontSize="small" />
                                                </ListItemIcon>
                                                <ListItemText>Delete</ListItemText>
                                            </MenuItem>
                                        </RowMenu>
                                    </TableCell>
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

export default ClientListPage;
