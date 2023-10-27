import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import RowActionMenuButton from '../components/RowMenu.tsx';
import SearchField from '../components/SearchField.tsx';
import AddIcon from '@mui/icons-material/Add';
import {
    Button,
    FormControl,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
} from '@mui/material';

const Spacer = () => <div style={{ flex: 1 }} />;

const ExecutorListPage = () => {
    return (
        <>
            <PageTitle>Executors</PageTitle>
            <ActionBar>
                <FormControl>
                    <Button startIcon={<AddIcon />} variant="contained">
                        Add
                    </Button>
                </FormControl>
                <Spacer />
                <SearchField onChange={() => {}} />
            </ActionBar>
            <TableContainer component={Paper}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell>ID</TableCell>
                            <TableCell>Type</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell width="1%"></TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        <TableRow>
                            <TableCell>default</TableCell>
                            <TableCell>ck8s-cli</TableCell>
                            <TableCell>always on</TableCell>
                            <TableCell align="right">
                                <RowActionMenuButton />
                            </TableCell>
                        </TableRow>
                    </TableBody>
                </Table>
            </TableContainer>
        </>
    );
};

export default ExecutorListPage;
