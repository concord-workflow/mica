import { DashboardRenderResponse, Layout, render } from '../api/dashboard.ts';
import CopyToClipboardButton from '../components/CopyToClipboardButton.tsx';
import PageTitle from '../components/PageTitle.tsx';
import EditIcon from '@mui/icons-material/Edit';
import LinkIcon from '@mui/icons-material/Link';
import { AlertTitle } from '@mui/lab';
import {
    Alert,
    Backdrop,
    Box,
    Button,
    CircularProgress,
    Container,
    FormControl,
    Paper,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';
import Grid from '@mui/material/Unstable_Grid2';

import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';

const Error = ({ message }: { message: string }) => {
    return (
        <Box m={2}>
            <Alert color="error">
                <AlertTitle>Dashboard Error</AlertTitle>
                {message}
            </Alert>
        </Box>
    );
};

const CopyPermalinkButton = ({ entityId }: { entityId: string | undefined }) => {
    if (!entityId) {
        return <></>;
    }
    return (
        <Typography variant="h6">
            <CopyToClipboardButton
                text={`${window.location.protocol}//${window.location.host}/mica/dashboard/${entityId}`}
                tooltipText="Copy permalink"
                Icon={LinkIcon}
            />
        </Typography>
    );
};
const renderCell = (row: string | boolean | number | unknown) => {
    if (typeof row === 'string' || typeof row === 'boolean' || typeof row === 'number') {
        return row;
    }
    return JSON.stringify(row);
};

const DashboardPage = () => {
    const navigate = useNavigate();

    const { entityId } = useParams();

    const { data, isLoading, error } = useQuery<DashboardRenderResponse, Error>({
        queryKey: ['dashboard', entityId],
        queryFn: () => render(entityId!),
        enabled: entityId !== undefined,
        retry: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
    });

    if (error) {
        return <Error message={error.message} />;
    }

    if (isLoading || !data) {
        return (
            <Backdrop open={true} sx={{ backgroundColor: '#fff' }}>
                <CircularProgress />
            </Backdrop>
        );
    }

    if (data.dashboard.layout !== Layout.TABLE) {
        return <Error message={`Unsupported layout: ${data.dashboard.layout}`} />;
    }

    const columns = data.dashboard.table?.columns;
    if (!columns) {
        return (
            <Error message="Invalid dashboard definition: 'table.columns' parameter is required for 'layout: TABLE' mode" />
        );
    }

    const rows = data.data;

    return (
        <Container sx={{ mt: 2 }} maxWidth="xl">
            <Grid container>
                <Grid xs={10}>
                    <PageTitle>{data.dashboard.title}</PageTitle>
                </Grid>
                <Grid xs={2} display="flex" justifyContent="flex-end">
                    <Stack direction="row" spacing={2}>
                        <FormControl>
                            <CopyPermalinkButton entityId={entityId} />
                        </FormControl>
                        <FormControl>
                            <Button
                                startIcon={<EditIcon />}
                                variant="outlined"
                                onClick={() => navigate(`/entity/${entityId}/edit`)}>
                                Edit
                            </Button>
                        </FormControl>
                    </Stack>
                </Grid>
            </Grid>
            <TableContainer sx={{ mt: 1 }} component={Paper}>
                <Table>
                    <TableHead>
                        <TableRow>
                            {columns.map((col) => (
                                <TableCell key={col.title}>{col.title}</TableCell>
                            ))}
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {rows.map((_, row) => (
                            <TableRow key={row}>
                                {columns.map((_, col) => (
                                    <TableCell key={col}>{renderCell(rows[row][col])}</TableCell>
                                ))}
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
        </Container>
    );
};

export default DashboardPage;
