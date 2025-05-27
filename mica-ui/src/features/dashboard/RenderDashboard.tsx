import { Layout, MicaDashboardV1 } from '../../api/dashboard.ts';
import {
    Alert,
    AlertTitle,
    Box,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
} from '@mui/material';

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

const renderCell = (row: string | boolean | number | unknown) => {
    if (typeof row === 'string' || typeof row === 'boolean' || typeof row === 'number') {
        return row;
    }
    return JSON.stringify(row);
};

interface Props {
    dashboard: MicaDashboardV1;
    data: Array<Array<string | boolean | number>>;
}

const RenderDashboard = ({ dashboard, data }: Props) => {
    if (dashboard.layout !== Layout.TABLE) {
        return <Error message={`Unsupported layout: ${dashboard.layout}`} />;
    }

    const columns = dashboard.table?.columns;
    if (!columns) {
        return (
            <Error message="Invalid dashboard definition: 'table.columns' parameter is required for 'layout: TABLE' mode" />
        );
    }

    return (
        <TableContainer component={Paper}>
            <Table>
                <TableHead>
                    <TableRow>
                        {columns.map((col) => (
                            <TableCell key={col.title}>{col.title}</TableCell>
                        ))}
                    </TableRow>
                </TableHead>
                <TableBody>
                    {data.map((_, row) => (
                        <TableRow key={row}>
                            {columns.map((_, col) => (
                                <TableCell key={col}>{renderCell(data[row][col])}</TableCell>
                            ))}
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </TableContainer>
    );
};

export default RenderDashboard;
