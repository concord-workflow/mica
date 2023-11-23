import { listEntities } from '../api/entity.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import RowMenu from '../components/RowMenu.tsx';
import SearchField from '../components/SearchField.tsx';
import Spacer from '../components/Spacer.tsx';
import highlightSubstring from '../components/highlight.tsx';
import UploadEntityDialog from '../features/UploadEntityDialog.tsx';
import AddIcon from '@mui/icons-material/Add';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DeleteIcon from '@mui/icons-material/Delete';
import {
    Button,
    CircularProgress,
    Container,
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
import { Link as RouterLink, useNavigate } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>Entities</b> page provides overview of all entities registered in Mica. Use the{' '}
        <b>upload</b> feature to import entities from YAML files.
    </>
);

const EntityListPage = () => {
    const [openUpload, setOpenUpload] = useState(false);

    const [search, setSearch] = useState<string>('');
    const { data, isFetching } = useQuery(['entity', 'list', search], () => listEntities(search), {
        keepPreviousData: true,
        select: ({ data }) => data.sort((a, b) => a.name.localeCompare(b.name)),
    });

    const [openSuccessNotification, setOpenSuccessNotification] = useState(false);
    const handleSuccessfulUpload = () => {
        setOpenSuccessNotification(true);
        setOpenUpload(false);
    };

    const navigate = useNavigate();

    return (
        <Container sx={{ mt: 2 }} maxWidth="xl">
            <PageTitle help={HELP}>Entities</PageTitle>
            <UploadEntityDialog
                open={openUpload}
                onSuccess={handleSuccessfulUpload}
                onClose={() => setOpenUpload(false)}
            />
            <Snackbar
                open={openSuccessNotification}
                autoHideDuration={5000}
                onClose={() => setOpenSuccessNotification(false)}
                message="Data uploaded successfully"
            />
            <ActionBar sx={{ mb: 2 }}>
                <FormControl>
                    <Button
                        startIcon={<AddIcon />}
                        variant="contained"
                        onClick={() => navigate('/entity/_new/edit')}>
                        Create
                    </Button>
                </FormControl>
                <FormControl>
                    <Button
                        startIcon={<CloudUploadIcon />}
                        variant="outlined"
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
                            <TableCell width="10%">Kind</TableCell>
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
                                    <TableCell>{row.kind}</TableCell>
                                    <TableCell>
                                        <Link
                                            component={RouterLink}
                                            to={`/entity/${row.id}/details`}>
                                            {highlightSubstring(row.name, search)}
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
        </Container>
    );
};

export default EntityListPage;
